/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Bogdan Stefanescu
 *     Florent Guillaume
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */
package org.nuxeo.ecm.core.schema.types.primitives;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.schema.types.PrimitiveType;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.EnumConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NotNullConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.NumericIntervalConstraint;
import org.nuxeo.ecm.core.schema.types.constraints.PatternConstraint;

/**
 * The integer type.
 */
public final class IntegerType extends PrimitiveType {

    private static final long serialVersionUID = 1L;

    public static final String ID = "integer";

    public static final IntegerType INSTANCE = new IntegerType();

    private IntegerType() {
        super(ID);
    }

    @Override
    public boolean validate(Object object) {
        return object instanceof Number;
    }

    @Override
    public Object convert(Object value) {
        if (value instanceof Integer) {
            return value;
        } else if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        } else {
            try {
                return Integer.valueOf((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    @Override
    public Object decode(String str) {
        if (StringUtils.isEmpty(str)) {
            return null;
        }
        // if strict validation is enabled, throw when input string cannot be decoded as an Integer
        if (isStrictValidation()) {
            return Integer.valueOf(str);
        }
        // strict validation not enabled, fall back on default value: 0
        try {
            return Integer.valueOf(str);
        } catch (NumberFormatException e) {
            return Integer.valueOf(0);
        }
    }

    @Override
    public String encode(Object object) {
        if (object instanceof Integer) {
            return object.toString();
        } else if (object instanceof Number) {
            return object.toString();
        } else {
            return object != null ? (String) object : "";
        }
    }

    protected Object readResolve() {
        return INSTANCE;
    }

    @Override
    public boolean support(Class<? extends Constraint> constraint) {
        if (NotNullConstraint.class.equals(constraint)) {
            return true;
        }
        if (EnumConstraint.class.equals(constraint)) {
            return true;
        }
        if (PatternConstraint.class.equals(constraint)) {
            return true;
        }
        if (NumericIntervalConstraint.class.equals(constraint)) {
            return true;
        }
        return false;
    }

}
