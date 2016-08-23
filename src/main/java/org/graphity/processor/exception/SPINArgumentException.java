/*
 * Copyright 2016 Martynas Jusevičius <martynas@graphity.org>.
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

package org.graphity.processor.exception;

import org.apache.jena.rdf.model.Resource;
import org.topbraid.spin.model.Template;

/**
 *
 * @author Martynas Jusevičius <martynas@graphity.org>
 */
public class SPINArgumentException extends RuntimeException
{

    public SPINArgumentException(String paramName, Template template)
    {
        super("Parameter '" + paramName + "' not supported by SPIN template '" + template.toString() + "'");
    }

    public SPINArgumentException(Resource command)
    {
        super("Parameters not supported by SPIN command '" + command.toString() + "'");
    }
    
}
