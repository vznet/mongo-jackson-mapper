/*
 * Copyright 2011 VZ Netzwerke Ltd
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
 */
package net.vz.mongodb.jackson.internal.object;

import com.mongodb.DBRef;
import org.bson.BSONObject;
import org.bson.types.ObjectId;
import org.codehaus.jackson.JsonStreamContext;
import org.codehaus.jackson.JsonToken;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

/**
 * Helper class for MongoDbObjectJsonParser
 *
 * @author James Roper
 * @since 1.0
 */
abstract class BsonObjectCursor extends JsonStreamContext {
    /**
     * Parent cursor of this cursor, if any; null for root
     * cursors.
     */
    private final BsonObjectCursor parent;

    public BsonObjectCursor(int contextType, BsonObjectCursor p) {
        super();
        _type = contextType;
        _index = -1;
        parent = p;
    }

    // note: co-variant return type
    @Override
    public final BsonObjectCursor getParent() {
        return parent;
    }

    @Override
    public abstract String getCurrentName();

    public abstract JsonToken nextToken();

    public abstract JsonToken endToken();

    public abstract Object currentNode();

    public boolean currentHasChildren() {
        Object o = currentNode();
        if (o instanceof Collection) {
            return !((Collection) o).isEmpty();
        } else if (o instanceof Map) {
            return !((Map) o).isEmpty();
        }
        return currentNode() != null;
    }

    /**
     * Method called to create a new context for iterating all
     * contents of the currentFieldName structured value (JSON array or object)
     *
     * @return A cursor for the children
     */
    public final BsonObjectCursor iterateChildren() {
        Object n = currentNode();
        if (n == null) throw new IllegalStateException("No current node");
        if (n instanceof Iterable) { // false since we have already returned START_ARRAY
            return new ArrayCursor((Iterable) n, this);
        }
        if (n instanceof BSONObject) {
            return new ObjectCursor((BSONObject) n, this);
        }
        throw new IllegalStateException("Current node of type " + n.getClass().getName());
    }

    /**
     * Cursor used for traversing iterables
     */
    protected final static class ArrayCursor extends BsonObjectCursor {
        Iterator<?> contents;

        Object currentNode;

        public ArrayCursor(Iterable n, BsonObjectCursor p) {
            super(JsonStreamContext.TYPE_ARRAY, p);
            contents = n.iterator();
        }

        @Override
        public String getCurrentName() {
            return null;
        }

        @Override
        public JsonToken nextToken() {
            if (!contents.hasNext()) {
                currentNode = null;
                return null;
            }
            currentNode = contents.next();
            return getToken(currentNode);
        }

        @Override
        public JsonToken endToken() {
            return JsonToken.END_ARRAY;
        }

        @Override
        public Object currentNode() {
            return currentNode;
        }
    }

    /**
     * Cursor used for traversing non-empty JSON Object nodes
     */
    protected final static class ObjectCursor
            extends BsonObjectCursor {
        Iterator<String> fields;
        BSONObject object;
        String currentFieldName;

        boolean needField;

        public ObjectCursor(BSONObject object, BsonObjectCursor p) {
            super(JsonStreamContext.TYPE_OBJECT, p);
            this.object = object;
            this.fields = object.keySet().iterator();
            needField = true;
        }

        @Override
        public String getCurrentName() {
            return currentFieldName;
        }

        @Override
        public JsonToken nextToken() {
            // Need a new entry?
            if (needField) {
                if (!fields.hasNext()) {
                    currentFieldName = null;
                    return null;
                }
                needField = false;
                currentFieldName = fields.next();
                return JsonToken.FIELD_NAME;
            }
            needField = true;
            return getToken(object.get(currentFieldName));
        }

        @Override
        public JsonToken endToken() {
            return JsonToken.END_OBJECT;
        }

        @Override
        public Object currentNode() {
            return (currentFieldName == null) ? null : object.get(currentFieldName);
        }

    }

    private static JsonToken getToken(Object o) {
        if (o == null) {
            return JsonToken.VALUE_NULL;
        } else if (o instanceof Iterable) {
            return JsonToken.START_ARRAY;
        } else if (o instanceof BSONObject) {
            return JsonToken.START_OBJECT;
        } else if (o instanceof Number) {
            if (o instanceof Double || o instanceof Float || o instanceof BigDecimal) {
                return JsonToken.VALUE_NUMBER_FLOAT;
            } else {
                return JsonToken.VALUE_NUMBER_INT;
            }
        } else if (o instanceof Boolean) {
            if ((Boolean) o) {
                return JsonToken.VALUE_TRUE;
            } else {
                return JsonToken.VALUE_FALSE;
            }
        } else if (o instanceof CharSequence) {
            return JsonToken.VALUE_STRING;
        } else if (o instanceof ObjectId) {
            return JsonToken.VALUE_STRING;
        } else if (o instanceof DBRef) {
            return JsonToken.VALUE_EMBEDDED_OBJECT;
        } else if (o instanceof Date) {
            return JsonToken.VALUE_EMBEDDED_OBJECT;
        } else {
            throw new IllegalStateException("Don't know how to parse type: " + o.getClass());
        }
    }
}
