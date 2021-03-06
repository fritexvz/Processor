/*
 * Copyright 2016 Martynas Jusevičius <martynas@atomgraph.com>.
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
package com.atomgraph.server.provider;

import com.atomgraph.core.model.Service;
import com.atomgraph.processor.model.Application;
import com.atomgraph.processor.model.impl.ApplicationImpl;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.PerRequestTypeInjectableProvider;
import com.sun.jersey.spi.resource.Singleton;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.Providers;
import org.apache.jena.ontology.Ontology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Martynas Jusevičius <martynas@atomgraph.com>
 */
@Provider
@Singleton
public class ApplicationProvider extends PerRequestTypeInjectableProvider<Context, Application> implements ContextResolver<Application>
{

    private static final Logger log = LoggerFactory.getLogger(ApplicationProvider.class);
    
    @Context Providers providers;
    
    public ApplicationProvider()
    {
        super(Application.class);
    }
    
    @Override
    public Injectable<Application> getInjectable(ComponentContext ic, Context a)
    {
	return new Injectable<Application>()
	{
	    @Override
	    public Application getValue()
	    {
                return getApplication();
	    }
	};
    }

    @Override
    public Application getContext(Class<?> type)
    {
        return getApplication();
    }
    
    public Application getApplication()
    {
        return getApplication(getService(), getOntology());
    }
    
    public Application getApplication(Service service, Ontology ontology)
    {
        return new ApplicationImpl(service, ontology);
    }
    
    public Service getService()
    {
	return getProviders().getContextResolver(Service.class, null).getContext(Service.class);
    }
    
    public Ontology getOntology()
    {
	return getProviders().getContextResolver(Ontology.class, null).getContext(Ontology.class);
    }
    
    public Providers getProviders()
    {
        return providers;
    }
    
}
