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

package org.elasticsearch.search.facet.termsstats.strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Scorer;
import org.elasticsearch.common.CacheRecycler;
import org.elasticsearch.common.lucene.HashedBytesRef;
import org.elasticsearch.common.trove.ExtTHashMap;
import org.elasticsearch.index.fielddata.DoubleValues;
import org.elasticsearch.index.fielddata.HashedBytesValues;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.IndexNumericFieldData;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.facet.AbstractFacetCollector;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.termsstats.TermsStatsFacet;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class TermsStatsStringFacetCollector extends AbstractFacetCollector {

    private final TermsStatsFacet.ComparatorType comparatorType;

    private final IndexFieldData keyIndexFieldData;
    private final IndexNumericFieldData valueIndexFieldData;
    private final SearchScript script;

    private final int size;
    private final int numberOfShards;

    private final Aggregator aggregator;

    private HashedBytesValues keyValues;

    public TermsStatsStringFacetCollector(String facetName, IndexFieldData keyIndexFieldData, IndexNumericFieldData valueIndexFieldData, SearchScript valueScript,
                                          int size, TermsStatsFacet.ComparatorType comparatorType, SearchContext context) {
        super(facetName);
        this.keyIndexFieldData = keyIndexFieldData;
        this.valueIndexFieldData = valueIndexFieldData;
        this.script = valueScript;
        this.size = size;
        this.comparatorType = comparatorType;
        this.numberOfShards = context.numberOfShards();

        if (script != null) {
            this.aggregator = new ScriptAggregator(valueScript);
        } else {
            this.aggregator = new Aggregator();
        }
    }

    @Override
    public void setScorer(Scorer scorer) throws IOException {
        if (script != null) {
            script.setScorer(scorer);
        }
    }

    @Override
    protected void doSetNextReader(AtomicReaderContext context) throws IOException {
        keyValues = keyIndexFieldData.load(context).getHashedBytesValues();
        aggregator.keyValues = keyValues;
        if (script != null) {
            script.setNextReader(context);
        } else {
            aggregator.valueValues = valueIndexFieldData.load(context).getDoubleValues();
        }
    }

    @Override
    protected void doCollect(int doc) throws IOException {
        keyValues.forEachValueInDoc(doc, aggregator);
    }

    @Override
    public Facet facet() {
        if (aggregator.entries.isEmpty()) {
            return new InternalTermsStatsStringFacet(facetName, comparatorType, size, ImmutableList.<InternalTermsStatsStringFacet.StringEntry>of(), aggregator.missing);
        }
        if (size == 0) { // all terms
            // all terms, just return the collection, we will sort it on the way back
            return new InternalTermsStatsStringFacet(facetName, comparatorType, 0 /* indicates all terms*/, aggregator.entries.values(), aggregator.missing);
        }
        Object[] values = aggregator.entries.internalValues();
        Arrays.sort(values, (Comparator) comparatorType.comparator());

        List<InternalTermsStatsStringFacet.StringEntry> ordered = Lists.newArrayList();
        int limit = size;
        for (int i = 0; i < limit; i++) {
            InternalTermsStatsStringFacet.StringEntry value = (InternalTermsStatsStringFacet.StringEntry) values[i];
            if (value == null) {
                break;
            }
            ordered.add(value);
        }

        CacheRecycler.pushHashMap(aggregator.entries); // fine to push here, we are done with it
        return new InternalTermsStatsStringFacet(facetName, comparatorType, size, ordered, aggregator.missing);
    }

    public static class Aggregator implements HashedBytesValues.ValueInDocProc {

        final ExtTHashMap<HashedBytesRef, InternalTermsStatsStringFacet.StringEntry> entries = CacheRecycler.popHashMap();

        int missing = 0;

        HashedBytesValues keyValues;
        DoubleValues valueValues;

        ValueAggregator valueAggregator = new ValueAggregator();

        @Override
        public void onValue(int docId, HashedBytesRef value) {
            InternalTermsStatsStringFacet.StringEntry stringEntry = entries.get(value);
            if (stringEntry == null) {
                value = keyValues.makeSafe(value);
                stringEntry = new InternalTermsStatsStringFacet.StringEntry(value, 0, 0, 0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
                entries.put(value, stringEntry);
            }
            stringEntry.count++;
            valueAggregator.stringEntry = stringEntry;
            valueValues.forEachValueInDoc(docId, valueAggregator);
        }

        @Override
        public void onMissing(int docId) {
            missing++;
        }

        public static class ValueAggregator implements DoubleValues.ValueInDocProc {

            InternalTermsStatsStringFacet.StringEntry stringEntry;

            @Override
            public void onMissing(int docId) {
            }

            @Override
            public void onValue(int docId, double value) {
                if (value < stringEntry.min) {
                    stringEntry.min = value;
                }
                if (value > stringEntry.max) {
                    stringEntry.max = value;
                }
                stringEntry.total += value;
                stringEntry.totalCount++;
            }
        }
    }

    public static class ScriptAggregator extends Aggregator {
        private final SearchScript script;

        public ScriptAggregator(SearchScript script) {
            this.script = script;
        }

        @Override
        public void onValue(int docId, HashedBytesRef value) {
            InternalTermsStatsStringFacet.StringEntry stringEntry = entries.get(value);
            if (stringEntry == null) {
                value = keyValues.makeSafe(value);
                stringEntry = new InternalTermsStatsStringFacet.StringEntry(value, 1, 0, 0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
                entries.put(value, stringEntry);
            } else {
                stringEntry.count++;
            }

            script.setNextDocId(docId);
            double valueValue = script.runAsDouble();
            if (valueValue < stringEntry.min) {
                stringEntry.min = valueValue;
            }
            if (valueValue > stringEntry.max) {
                stringEntry.max = valueValue;
            }
            stringEntry.total += valueValue;
            stringEntry.totalCount++;
        }
    }
}