/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
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

package org.elasticsearch.index.mapper.nested;

import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.MapperTests;
import org.elasticsearch.index.mapper.ParsedDocument;
import org.elasticsearch.index.mapper.internal.TypeFieldMapper;
import org.elasticsearch.index.mapper.object.ObjectMapper;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

@Test
public class NestedMappingTests {

    @Test public void singleNested() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type").startObject("properties")
                .startObject("nested1").field("type", "nested").endObject()
                .endObject().endObject().endObject().string();

        DocumentMapper docMapper = MapperTests.newParser().parse(mapping);

        assertThat(docMapper.hasNestedObjects(), equalTo(true));
        ObjectMapper nested1Mapper = docMapper.objectMappers().get("nested1");
        assertThat(nested1Mapper.nested(), equalTo(ObjectMapper.Nested.NESTED));

        ParsedDocument doc = docMapper.parse("type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .startObject("nested1").field("field1", "1").field("field2", "2").endObject()
                .endObject()
                .copiedBytes());

        assertThat(doc.docs().size(), equalTo(2));
        assertThat(doc.docs().get(0).get(TypeFieldMapper.NAME), equalTo(nested1Mapper.nestedTypePath()));
        assertThat(doc.docs().get(0).get("nested1.field1"), equalTo("1"));
        assertThat(doc.docs().get(0).get("nested1.field2"), equalTo("2"));

        assertThat(doc.docs().get(1).get("field"), equalTo("value"));


        doc = docMapper.parse("type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .startArray("nested1")
                .startObject().field("field1", "1").field("field2", "2").endObject()
                .startObject().field("field1", "3").field("field2", "4").endObject()
                .endArray()
                .endObject()
                .copiedBytes());

        assertThat(doc.docs().size(), equalTo(3));
        assertThat(doc.docs().get(0).get(TypeFieldMapper.NAME), equalTo(nested1Mapper.nestedTypePath()));
        assertThat(doc.docs().get(0).get("nested1.field1"), equalTo("3"));
        assertThat(doc.docs().get(0).get("nested1.field2"), equalTo("4"));
        assertThat(doc.docs().get(1).get(TypeFieldMapper.NAME), equalTo(nested1Mapper.nestedTypePath()));
        assertThat(doc.docs().get(1).get("nested1.field1"), equalTo("1"));
        assertThat(doc.docs().get(1).get("nested1.field2"), equalTo("2"));

        assertThat(doc.docs().get(2).get("field"), equalTo("value"));
    }

    @Test public void multiNested() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type").startObject("properties")
                .startObject("nested1").field("type", "nested").startObject("properties")
                .startObject("nested2").field("type", "nested")
                .endObject().endObject()
                .endObject().endObject().endObject().string();

        DocumentMapper docMapper = MapperTests.newParser().parse(mapping);

        assertThat(docMapper.hasNestedObjects(), equalTo(true));
        ObjectMapper nested1Mapper = docMapper.objectMappers().get("nested1");
        assertThat(nested1Mapper.nested(), equalTo(ObjectMapper.Nested.NESTED));
        ObjectMapper nested2Mapper = docMapper.objectMappers().get("nested1.nested2");
        assertThat(nested2Mapper.nested(), equalTo(ObjectMapper.Nested.NESTED));

        ParsedDocument doc = docMapper.parse("type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .startArray("nested1")
                .startObject().field("field1", "1").startArray("nested2").startObject().field("field2", "2").endObject().startObject().field("field2", "3").endObject().endArray().endObject()
                .startObject().field("field1", "4").startArray("nested2").startObject().field("field2", "5").endObject().startObject().field("field2", "6").endObject().endArray().endObject()
                .endArray()
                .endObject()
                .copiedBytes());

        assertThat(doc.docs().size(), equalTo(7));
        assertThat(doc.docs().get(0).get("nested1.nested2.field2"), equalTo("6"));
        assertThat(doc.docs().get(0).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(0).get("field"), nullValue());
        assertThat(doc.docs().get(1).get("nested1.nested2.field2"), equalTo("5"));
        assertThat(doc.docs().get(1).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(1).get("field"), nullValue());
        assertThat(doc.docs().get(2).get("nested1.field1"), equalTo("4"));
        assertThat(doc.docs().get(2).get("nested1.nested2.field2"), nullValue());
        assertThat(doc.docs().get(2).get("field"), nullValue());
        assertThat(doc.docs().get(3).get("nested1.nested2.field2"), equalTo("3"));
        assertThat(doc.docs().get(3).get("field"), nullValue());
        assertThat(doc.docs().get(4).get("nested1.nested2.field2"), equalTo("2"));
        assertThat(doc.docs().get(4).get("field"), nullValue());
        assertThat(doc.docs().get(5).get("nested1.field1"), equalTo("1"));
        assertThat(doc.docs().get(5).get("nested1.nested2.field2"), nullValue());
        assertThat(doc.docs().get(5).get("field"), nullValue());
        assertThat(doc.docs().get(6).get("field"), equalTo("value"));
        assertThat(doc.docs().get(6).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(6).get("nested1.nested2.field2"), nullValue());
    }

    @Test public void multiObjectAndNested1() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type").startObject("properties")
                .startObject("nested1").field("type", "nested").startObject("properties")
                .startObject("nested2").field("type", "object_and_nested")
                .endObject().endObject()
                .endObject().endObject().endObject().string();

        DocumentMapper docMapper = MapperTests.newParser().parse(mapping);

        assertThat(docMapper.hasNestedObjects(), equalTo(true));
        ObjectMapper nested1Mapper = docMapper.objectMappers().get("nested1");
        assertThat(nested1Mapper.nested(), equalTo(ObjectMapper.Nested.NESTED));
        ObjectMapper nested2Mapper = docMapper.objectMappers().get("nested1.nested2");
        assertThat(nested2Mapper.nested(), equalTo(ObjectMapper.Nested.OBJECT_AND_NESTED));

        ParsedDocument doc = docMapper.parse("type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .startArray("nested1")
                .startObject().field("field1", "1").startArray("nested2").startObject().field("field2", "2").endObject().startObject().field("field2", "3").endObject().endArray().endObject()
                .startObject().field("field1", "4").startArray("nested2").startObject().field("field2", "5").endObject().startObject().field("field2", "6").endObject().endArray().endObject()
                .endArray()
                .endObject()
                .copiedBytes());

        assertThat(doc.docs().size(), equalTo(7));
        assertThat(doc.docs().get(0).get("nested1.nested2.field2"), equalTo("6"));
        assertThat(doc.docs().get(0).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(0).get("field"), nullValue());
        assertThat(doc.docs().get(1).get("nested1.nested2.field2"), equalTo("5"));
        assertThat(doc.docs().get(1).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(1).get("field"), nullValue());
        assertThat(doc.docs().get(2).get("nested1.field1"), equalTo("4"));
        assertThat(doc.docs().get(2).get("nested1.nested2.field2"), equalTo("5"));
        assertThat(doc.docs().get(2).get("field"), nullValue());
        assertThat(doc.docs().get(3).get("nested1.nested2.field2"), equalTo("3"));
        assertThat(doc.docs().get(3).get("field"), nullValue());
        assertThat(doc.docs().get(4).get("nested1.nested2.field2"), equalTo("2"));
        assertThat(doc.docs().get(4).get("field"), nullValue());
        assertThat(doc.docs().get(5).get("nested1.field1"), equalTo("1"));
        assertThat(doc.docs().get(5).get("nested1.nested2.field2"), equalTo("2"));
        assertThat(doc.docs().get(5).get("field"), nullValue());
        assertThat(doc.docs().get(6).get("field"), equalTo("value"));
        assertThat(doc.docs().get(6).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(6).get("nested1.nested2.field2"), nullValue());
    }

    @Test public void multiObjectAndNested2() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type").startObject("properties")
                .startObject("nested1").field("type", "object_and_nested").startObject("properties")
                .startObject("nested2").field("type", "object_and_nested")
                .endObject().endObject()
                .endObject().endObject().endObject().string();

        DocumentMapper docMapper = MapperTests.newParser().parse(mapping);

        assertThat(docMapper.hasNestedObjects(), equalTo(true));
        ObjectMapper nested1Mapper = docMapper.objectMappers().get("nested1");
        assertThat(nested1Mapper.nested(), equalTo(ObjectMapper.Nested.OBJECT_AND_NESTED));
        ObjectMapper nested2Mapper = docMapper.objectMappers().get("nested1.nested2");
        assertThat(nested2Mapper.nested(), equalTo(ObjectMapper.Nested.OBJECT_AND_NESTED));

        ParsedDocument doc = docMapper.parse("type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .startArray("nested1")
                .startObject().field("field1", "1").startArray("nested2").startObject().field("field2", "2").endObject().startObject().field("field2", "3").endObject().endArray().endObject()
                .startObject().field("field1", "4").startArray("nested2").startObject().field("field2", "5").endObject().startObject().field("field2", "6").endObject().endArray().endObject()
                .endArray()
                .endObject()
                .copiedBytes());

        assertThat(doc.docs().size(), equalTo(7));
        assertThat(doc.docs().get(0).get("nested1.nested2.field2"), equalTo("6"));
        assertThat(doc.docs().get(0).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(0).get("field"), nullValue());
        assertThat(doc.docs().get(1).get("nested1.nested2.field2"), equalTo("5"));
        assertThat(doc.docs().get(1).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(1).get("field"), nullValue());
        assertThat(doc.docs().get(2).get("nested1.field1"), equalTo("4"));
        assertThat(doc.docs().get(2).get("nested1.nested2.field2"), equalTo("5"));
        assertThat(doc.docs().get(2).get("field"), nullValue());
        assertThat(doc.docs().get(3).get("nested1.nested2.field2"), equalTo("3"));
        assertThat(doc.docs().get(3).get("field"), nullValue());
        assertThat(doc.docs().get(4).get("nested1.nested2.field2"), equalTo("2"));
        assertThat(doc.docs().get(4).get("field"), nullValue());
        assertThat(doc.docs().get(5).get("nested1.field1"), equalTo("1"));
        assertThat(doc.docs().get(5).get("nested1.nested2.field2"), equalTo("2"));
        assertThat(doc.docs().get(5).get("field"), nullValue());
        assertThat(doc.docs().get(6).get("field"), equalTo("value"));
        assertThat(doc.docs().get(6).getFieldables("nested1.field1").length, equalTo(2));
        assertThat(doc.docs().get(6).getFieldables("nested1.nested2.field2").length, equalTo(4));
    }

    @Test public void multiRootAndNested1() throws Exception {
        String mapping = XContentFactory.jsonBuilder().startObject().startObject("type").startObject("properties")
                .startObject("nested1").field("type", "nested").startObject("properties")
                .startObject("nested2").field("type", "root_and_nested")
                .endObject().endObject()
                .endObject().endObject().endObject().string();

        DocumentMapper docMapper = MapperTests.newParser().parse(mapping);

        assertThat(docMapper.hasNestedObjects(), equalTo(true));
        ObjectMapper nested1Mapper = docMapper.objectMappers().get("nested1");
        assertThat(nested1Mapper.nested(), equalTo(ObjectMapper.Nested.NESTED));
        ObjectMapper nested2Mapper = docMapper.objectMappers().get("nested1.nested2");
        assertThat(nested2Mapper.nested(), equalTo(ObjectMapper.Nested.ROOT_AND_NESTED));

        ParsedDocument doc = docMapper.parse("type", "1", XContentFactory.jsonBuilder()
                .startObject()
                .field("field", "value")
                .startArray("nested1")
                .startObject().field("field1", "1").startArray("nested2").startObject().field("field2", "2").endObject().startObject().field("field2", "3").endObject().endArray().endObject()
                .startObject().field("field1", "4").startArray("nested2").startObject().field("field2", "5").endObject().startObject().field("field2", "6").endObject().endArray().endObject()
                .endArray()
                .endObject()
                .copiedBytes());

        assertThat(doc.docs().size(), equalTo(7));
        assertThat(doc.docs().get(0).get("nested1.nested2.field2"), equalTo("6"));
        assertThat(doc.docs().get(0).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(0).get("field"), nullValue());
        assertThat(doc.docs().get(1).get("nested1.nested2.field2"), equalTo("5"));
        assertThat(doc.docs().get(1).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(1).get("field"), nullValue());
        assertThat(doc.docs().get(2).get("nested1.field1"), equalTo("4"));
        assertThat(doc.docs().get(2).get("nested1.nested2.field2"), nullValue());
        assertThat(doc.docs().get(2).get("field"), nullValue());
        assertThat(doc.docs().get(3).get("nested1.nested2.field2"), equalTo("3"));
        assertThat(doc.docs().get(3).get("field"), nullValue());
        assertThat(doc.docs().get(4).get("nested1.nested2.field2"), equalTo("2"));
        assertThat(doc.docs().get(4).get("field"), nullValue());
        assertThat(doc.docs().get(5).get("nested1.field1"), equalTo("1"));
        assertThat(doc.docs().get(5).get("nested1.nested2.field2"), nullValue());
        assertThat(doc.docs().get(5).get("field"), nullValue());
        assertThat(doc.docs().get(6).get("field"), equalTo("value"));
        assertThat(doc.docs().get(6).get("nested1.field1"), nullValue());
        assertThat(doc.docs().get(6).getFieldables("nested1.nested2.field2").length, equalTo(4));
    }
}