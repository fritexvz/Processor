/*
 * Copyright 2015 Martynas Jusevičius <martynas@graphity.org>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.graphity.server.filter.response;

import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.vocabulary.RDF;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.Family.REDIRECTION;
import javax.ws.rs.ext.Provider;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.jena.ext.com.google.common.base.Charsets;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.graphity.core.util.Link;
import org.graphity.core.util.StateBuilder;
import org.graphity.processor.exception.ArgumentException;
import org.graphity.processor.exception.SitemapException;
import org.graphity.processor.model.Template;
import org.graphity.processor.model.TemplateCall;
import org.graphity.server.provider.OntologyProvider;
import org.graphity.processor.util.RDFNodeFactory;
import org.graphity.processor.vocabulary.LDT;
import org.graphity.processor.vocabulary.LDTC;
import org.graphity.processor.vocabulary.LDTDH;
import org.graphity.processor.vocabulary.XHV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.model.Argument;

/**
 * A filter that adds HATEOAS transitions to the RDF query result.
 * 
 * @author Martynas Jusevičius <martynas@graphity.org>
 * @see <a href="https://www.ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm">Representational State Transfer (REST): chapter 5</a>
 */
@Provider
public class HypermediaFilter implements ContainerResponseFilter
{
    private static final Logger log = LoggerFactory.getLogger(HypermediaFilter.class);
            
    @Override
    public ContainerResponse filter(ContainerRequest request, ContainerResponse response)
    {
        if (request == null) throw new IllegalArgumentException("ContainerRequest cannot be null");
        if (response == null) throw new IllegalArgumentException("ContainerResponse cannot be null");
        
        // do not process hypermedia if the response is a redirect or returns the body of bad request
        if (response.getStatusType().getFamily().equals(REDIRECTION) || // response.getStatusType().equals(Response.Status.BAD_REQUEST) ||
                response.getEntity() == null || (!(response.getEntity() instanceof Model)))
            return response;
        
        MultivaluedMap<String, Object> headerMap = response.getHttpHeaders();
        try
        {
            URI ontologyHref = getOntologyURI(headerMap);
            URI typeHref = getTypeURI(headerMap);
            if (ontologyHref == null || typeHref == null) return response;
            Object rulesString = response.getHttpHeaders().getFirst("Rules");
            if (rulesString == null) return response;

            OntologyProvider provider = new OntologyProvider(null);
            Ontology ontology = provider.getOntology(ontologyHref.toString(), provider.getOntModelSpec(Rule.parseRules(rulesString.toString())));
            if (ontology == null) throw new SitemapException("Ontology resource '" + ontologyHref.toString() + "'not found in ontology graph");
            Template template = ontology.getOntModel().getOntClass(typeHref.toString()).as(Template.class);
            
            List<NameValuePair> queryParams = URLEncodedUtils.parse(request.getRequestUri(), Charsets.UTF_8.name());
            TemplateCall templateCall = ontology.getOntModel().createIndividual(LDT.TemplateCall).
                addProperty(LDT.template, template).
                as(TemplateCall.class).applyArguments(queryParams);

            Model model = ModelFactory.createDefaultModel();
            Resource absolutePath = model.createResource(request.getAbsolutePath().toString());
            Resource requestUri = model.createResource(request.getRequestUri().toString());

            // transition to a URI of another application state (HATEOAS)
            Resource pageState = applyTemplateCall(StateBuilder.fromResource(requestUri), templateCall).build();
            if (!pageState.getURI().equals(request.getRequestUri().toString()))
            {
                if (log.isDebugEnabled()) log.debug("Redirecting to a state transition URI: {}", pageState.getURI());
                response.setResponse(Response.seeOther(URI.create(pageState.getURI())).build());
                return response;
            }                    

            StateBuilder viewBuilder = StateBuilder.fromResource(absolutePath);
            Resource view = applyArguments(viewBuilder, templateCall, queryParams).build();
            if (!view.equals(absolutePath)) // add hypermedia if there are query parameters
            {
                view.addProperty(LDTC.viewOf, absolutePath).
                    addProperty(RDF.type, LDTC.View);

                if (templateCall.hasProperty(LDTDH.limit)) // pages must have limits
                {
                    if (log.isDebugEnabled()) log.debug("Adding Page metadata: {} gp:pageOf {}", view, absolutePath);
                    view.addProperty(LDTDH.pageOf, absolutePath).
                    addProperty(RDF.type, LDTDH.Page); // do we still need gp:Page now that we have gp:View?

                    addPrevNextPage(absolutePath, viewBuilder, templateCall);
                }
            }

            if (log.isDebugEnabled()) log.debug("Added Number of HATEOAS statements added: {}", model.size());
            response.setEntity(model.add((Model)response.getEntity()));
        }
        catch (URISyntaxException ex)
        {
            return response;
        }
        
        return response;
    }
    
    public StateBuilder applyArguments(StateBuilder stateBuilder, TemplateCall templateCall, List<NameValuePair> params)
    {
        if (stateBuilder == null) throw new IllegalArgumentException("Resource cannot be null");
        if (templateCall == null) throw new IllegalArgumentException("Templatecall cannot be null");
        if (params == null) throw new IllegalArgumentException("Param List cannot be null");
        
        Iterator <NameValuePair> it = params.iterator();
        while (it.hasNext())
        {
            NameValuePair pair = it.next();
            String paramName = pair.getName();
            String paramValue = pair.getValue();

            Argument arg = templateCall.getTemplate().getArgumentsMap().get(paramName);
            if (arg == null) throw new ArgumentException(paramName, templateCall.getTemplate());

            stateBuilder.property(arg.getPredicate(), RDFNodeFactory.createTyped(paramValue, arg.getValueType()));
        }
        
        return stateBuilder;
    }
    
    public StateBuilder applyTemplateCall(StateBuilder sb, TemplateCall templateCall)
    {
        if (sb == null) throw new IllegalArgumentException("Resource cannot be null");
        if (templateCall == null) throw new IllegalArgumentException("TemplateCall cannot be null");
        
        StmtIterator it = templateCall.listProperties();
        try
        {
            while (it.hasNext())
            {
                Statement stmt = it.next();
                // ignore system properties on TemplateCall. TO-DO: find a better solution to this
                if (!stmt.getPredicate().equals(RDF.type) && !stmt.getPredicate().equals(LDT.template))
                    sb.replaceProperty(stmt.getPredicate(), stmt.getObject());
            }
        }
        finally
        {
            it.close();
        }
        
        return sb;
    }

    public void addPrevNextPage(Resource absolutePath, StateBuilder pageBuilder, TemplateCall pageCall)
    {
        if (absolutePath == null) throw new IllegalArgumentException("Resource cannot be null");
        if (pageBuilder == null) throw new IllegalArgumentException("StateBuilder cannot be null");
        if (pageCall == null) throw new IllegalArgumentException("TemplateCall cannot be null");
        
        //if (pageCall.hasProperty(LDT.limit))
        {
            Resource page = pageBuilder.build();
            Long limit = pageCall.getProperty(LDTDH.limit).getLong();            
            Long offset = Long.valueOf(0);
            if (pageCall.hasProperty(LDTDH.offset)) offset = pageCall.getProperty(LDTDH.offset).getLong();
            
            if (offset >= limit)
            {
                TemplateCall prevCall = pageCall.removeAll(LDTDH.offset).
                    addLiteral(LDTDH.offset, offset - limit).
                    as(TemplateCall.class);
                Resource prev = applyTemplateCall(pageBuilder, prevCall).build().
                    addProperty(LDTDH.pageOf, absolutePath).
                    addProperty(RDF.type, LDTDH.Page).
                    addProperty(XHV.next, page);

                if (log.isDebugEnabled()) log.debug("Adding page metadata: {} xhv:previous {}", page, prev);
                page.addProperty(XHV.prev, prev);
            }

            TemplateCall nextCall = pageCall.removeAll(LDTDH.offset).
                addLiteral(LDTDH.offset, offset + limit).
                as(TemplateCall.class);
            Resource next = applyTemplateCall(pageBuilder, nextCall).build().
                addProperty(LDTDH.pageOf, absolutePath).
                addProperty(RDF.type, LDTDH.Page).
                addProperty(XHV.prev, page);

            if (log.isDebugEnabled()) log.debug("Adding page metadata: {} xhv:next {}", page, next);
            page.addProperty(XHV.next, next);
        }
        
        //return container;
    }
    
    public URI getTypeURI(MultivaluedMap<String, Object> headerMap) throws URISyntaxException
    {
        return getLinkHref(headerMap, "Link", RDF.type.getLocalName());
    }

    public URI getOntologyURI(MultivaluedMap<String, Object> headerMap) throws URISyntaxException
    {
        return getLinkHref(headerMap, "Link", LDT.ontology.getURI());
    }

    public URI getLinkHref(MultivaluedMap<String, Object> headerMap, String headerName, String rel) throws URISyntaxException
    {
	if (headerMap == null) throw new IllegalArgumentException("Header Map cannot be null");
	if (headerName == null) throw new IllegalArgumentException("String header name cannot be null");
        if (rel == null) throw new IllegalArgumentException("Property Map cannot be null");
        
        List<Object> links = headerMap.get(headerName);
        if (links != null)
        {
            Iterator<Object> it = links.iterator();
            while (it.hasNext())
            {
                String linkHeader = it.next().toString();
                Link link = Link.valueOf(linkHeader);
                if (link.getRel().equals(rel)) return link.getHref();
            }
        }
        
        return null;
    }
    
    public OntModelSpec getOntModelSpec(List<Rule> rules)
    {
        OntModelSpec ontModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM);
        
        if (rules != null)
        {
            Reasoner reasoner = new GenericRuleReasoner(rules);
            //reasoner.setDerivationLogging(true);
            //reasoner.setParameter(ReasonerVocabulary.PROPtraceOn, Boolean.TRUE);
            ontModelSpec.setReasoner(reasoner);
        }
        
        return ontModelSpec;
    }
    
}