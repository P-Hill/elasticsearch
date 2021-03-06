/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.fielddata;

/**
 * The thread safe {@link org.apache.lucene.index.AtomicReader} level cache of the data.
 */
public interface AtomicFieldData<Script extends ScriptDocValues> {

    /**
     * Does *one* of the docs contain multi values?
     */
    boolean isMultiValued();

    /**
     * Are the values ordered? (in ascending manner).
     */
    boolean isValuesOrdered();

    /**
     * The number of docs in this field data.
     */
    int getNumDocs();

    /**
     * Size (in bytes) of memory used by this field data.
     */
    long getMemorySizeInBytes();

    /**
     * Use a non thread safe (lightweight) view of the values as bytes.
     */
    BytesValues getBytesValues();

    /**
     * Use a non thread safe (lightweight) view of the values as bytes.
     */
    HashedBytesValues getHashedBytesValues();

    /**
     * Use a non thread safe (lightweight) view of the values as strings.
     */
    StringValues getStringValues();

    /**
     * Returns a "scripting" based values.
     */
    Script getScriptValues();

    interface WithOrdinals<Script extends ScriptDocValues> extends AtomicFieldData<Script> {

        /**
         * Use a non thread safe (lightweight) view of the values as bytes.
         */
        BytesValues.WithOrdinals getBytesValues();

        /**
         * Use a non thread safe (lightweight) view of the values as bytes.
         */
        HashedBytesValues.WithOrdinals getHashedBytesValues();

        /**
         * Use a non thread safe (lightweight) view of the values as strings.
         */
        StringValues.WithOrdinals getStringValues();
    }
}
