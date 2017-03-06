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

package com.atomgraph.processor.exception;

import com.atomgraph.processor.model.Template;
import com.atomgraph.processor.model.Parameter;

/**
 *
 * @author Martynas Jusevičius <martynas@atomgraph.com>
 */
public class ParameterException extends RuntimeException
{

    public ParameterException(String paramName, Template template)
    {
        super("Parameter '" + paramName + "' not supported by Template '" + template.toString() + "'");
    }

    public ParameterException(Parameter param, Template template)
    {
        super("Argument with predicate '" + param.getPredicate() + "' is not optional in Template '" + template.toString() + "' but no value is supplied");
    }
    
}