/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
package org.elasticsearch.search.aggregations;

import org.elasticsearch.Version;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.NamedWriteableAwareStreamInput;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.search.DocValueFormat;
import org.elasticsearch.search.SearchModule;
import org.elasticsearch.search.aggregations.bucket.histogram.InternalDateHistogram;
import org.elasticsearch.search.aggregations.bucket.histogram.InternalDateHistogramTests;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTermsTests;
import org.elasticsearch.search.aggregations.pipeline.InternalSimpleValue;
import org.elasticsearch.search.aggregations.pipeline.InternalSimpleValueTests;
import org.elasticsearch.search.aggregations.pipeline.SiblingPipelineAggregator;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.avg.AvgBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.max.MaxBucketPipelineAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.bucketmetrics.sum.SumBucketPipelineAggregationBuilder;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.test.VersionUtils;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class InternalAggregationsTests extends ESTestCase {

    private final NamedWriteableRegistry registry = new NamedWriteableRegistry(
        new SearchModule(Settings.EMPTY, false, Collections.emptyList()).getNamedWriteables());

    public void testReduceEmptyAggs() {
        List<InternalAggregations> aggs = Collections.emptyList();
        InternalAggregation.ReduceContext reduceContext = new InternalAggregation.ReduceContext(null, null, randomBoolean());
        assertNull(InternalAggregations.reduce(aggs, Collections.emptyList(), reduceContext));
    }

    public void testNonFinalReduceTopLevelPipelineAggs() throws IOException  {
        InternalAggregation terms = new StringTerms("name", BucketOrder.key(true),
            10, 1, Collections.emptyList(), Collections.emptyMap(), DocValueFormat.RAW, 25, false, 10, Collections.emptyList(), 0);
        List<InternalAggregations> aggs = Collections.singletonList(new InternalAggregations(Collections.singletonList(terms)));
        List<SiblingPipelineAggregator> topLevelPipelineAggs = new ArrayList<>();
        MaxBucketPipelineAggregationBuilder maxBucketPipelineAggregationBuilder = new MaxBucketPipelineAggregationBuilder("test", "test");
        topLevelPipelineAggs.add((SiblingPipelineAggregator)maxBucketPipelineAggregationBuilder.create());
        InternalAggregation.ReduceContext reduceContext = new InternalAggregation.ReduceContext(null, null, false);
        InternalAggregations reducedAggs = InternalAggregations.reduce(aggs, topLevelPipelineAggs, reduceContext);
        assertEquals(1, reducedAggs.getTopLevelPipelineAggregators().size());
        assertEquals(1, reducedAggs.aggregations.size());
    }

    public void testFinalReduceTopLevelPipelineAggs() throws IOException  {
        InternalAggregation terms = new StringTerms("name", BucketOrder.key(true),
            10, 1, Collections.emptyList(), Collections.emptyMap(), DocValueFormat.RAW, 25, false, 10, Collections.emptyList(), 0);

        MaxBucketPipelineAggregationBuilder maxBucketPipelineAggregationBuilder = new MaxBucketPipelineAggregationBuilder("test", "test");
        SiblingPipelineAggregator siblingPipelineAggregator = (SiblingPipelineAggregator) maxBucketPipelineAggregationBuilder.create();
        InternalAggregation.ReduceContext reduceContext = new InternalAggregation.ReduceContext(null, null, true);
        final InternalAggregations reducedAggs;
        if (randomBoolean()) {
            InternalAggregations aggs = new InternalAggregations(Collections.singletonList(terms),
                Collections.singletonList(siblingPipelineAggregator));
            reducedAggs = InternalAggregations.reduce(Collections.singletonList(aggs), reduceContext);
        } else {
            InternalAggregations aggs = new InternalAggregations(Collections.singletonList(terms));
            List<SiblingPipelineAggregator> topLevelPipelineAggs = Collections.singletonList(siblingPipelineAggregator);
            reducedAggs = InternalAggregations.reduce(Collections.singletonList(aggs), topLevelPipelineAggs, reduceContext);
        }
        assertEquals(0, reducedAggs.getTopLevelPipelineAggregators().size());
        assertEquals(2, reducedAggs.aggregations.size());
    }

    public void testSerialization() throws Exception {
        List<InternalAggregation> aggsList = new ArrayList<>();
        if (randomBoolean()) {
            StringTermsTests stringTermsTests = new StringTermsTests();
            stringTermsTests.init();
            stringTermsTests.setUp();
            aggsList.add(stringTermsTests.createTestInstance());
        }
        if (randomBoolean()) {
            InternalDateHistogramTests dateHistogramTests = new InternalDateHistogramTests();
            dateHistogramTests.setUp();
            aggsList.add(dateHistogramTests.createTestInstance());
        }
        if (randomBoolean()) {
            InternalSimpleValueTests simpleValueTests = new InternalSimpleValueTests();
            aggsList.add(simpleValueTests.createTestInstance());
        }
        List<SiblingPipelineAggregator> topLevelPipelineAggs = new ArrayList<>();
        if (randomBoolean()) {
            if (randomBoolean()) {
                topLevelPipelineAggs.add((SiblingPipelineAggregator)new MaxBucketPipelineAggregationBuilder("name1", "bucket1").create());
            }
            if (randomBoolean()) {
                topLevelPipelineAggs.add((SiblingPipelineAggregator)new AvgBucketPipelineAggregationBuilder("name2", "bucket2").create());
            }
            if (randomBoolean()) {
                topLevelPipelineAggs.add((SiblingPipelineAggregator)new SumBucketPipelineAggregationBuilder("name3", "bucket3").create());
            }
        }
        InternalAggregations aggregations = new InternalAggregations(aggsList, topLevelPipelineAggs);
        writeToAndReadFrom(aggregations, 0);
    }

    private void writeToAndReadFrom(InternalAggregations aggregations, int iteration) throws IOException {
        Version version = VersionUtils.randomVersion(random());
        try (BytesStreamOutput out = new BytesStreamOutput()) {
            out.setVersion(version);
            aggregations.writeTo(out);
            try (StreamInput in = new NamedWriteableAwareStreamInput(StreamInput.wrap(out.bytes().toBytesRef().bytes), registry)) {
                in.setVersion(version);
                InternalAggregations deserialized = InternalAggregations.readAggregations(in);
                assertEquals(aggregations.aggregations, deserialized.aggregations);
                if (aggregations.getTopLevelPipelineAggregators() == null) {
                    assertEquals(0, deserialized.getTopLevelPipelineAggregators().size());
                } else {
                    if (version.before(Version.V_6_7_0)) {
                        assertEquals(0, deserialized.getTopLevelPipelineAggregators().size());
                    } else {
                        assertEquals(aggregations.getTopLevelPipelineAggregators().size(),
                            deserialized.getTopLevelPipelineAggregators().size());
                        for (int i = 0; i < aggregations.getTopLevelPipelineAggregators().size(); i++) {
                            SiblingPipelineAggregator siblingPipelineAggregator1 = aggregations.getTopLevelPipelineAggregators().get(i);
                            SiblingPipelineAggregator siblingPipelineAggregator2 = deserialized.getTopLevelPipelineAggregators().get(i);
                            assertArrayEquals(siblingPipelineAggregator1.bucketsPaths(), siblingPipelineAggregator2.bucketsPaths());
                            assertEquals(siblingPipelineAggregator1.name(), siblingPipelineAggregator2.name());
                        }
                    }
                }
                if (iteration < 2) {
                    //serialize this enough times to make sure that we are able to write again what we read
                    writeToAndReadFrom(deserialized, iteration + 1);
                }
            }
        }
    }

    public void testSerializationFromPre_6_7_0() throws IOException {
        String aggsString = "AwZzdGVybXMFb0F0Q0EKCQVsZG5ncgAFeG56RWcFeUFxVmcABXBhQVVpBUtYc2VIAAVaclRESwVqUkxySAAFelp5d1AFRUREcEYABW1" +
            "sckF0BU5wWWVFAAVJYVJmZgVURlJVbgAFT0RiU04FUWNwSVoABU1sb09HBUNzZHFlAAVWWmJHaQABAwGIDgNyYXcFAQAADmRhdGVfaGlzdG9ncmFt" +
            "BVhHbVl4/wADAAKAurcDA1VUQwABAQAAAWmOhukAAQAAAWmR9dEAAAAAAAAAAAAAAANyYXcACAAAAWmQrDoAUQAAAAFpkRoXAEMAAAABaZGH9AAtA" +
            "AAAAWmR9dEAJwAAAAFpkmOuAFwAAAABaZLRiwAYAAAAAWmTP2gAKgAAAAFpk61FABsADHNpbXBsZV92YWx1ZQVsWVNLVv8AB2RlY2ltYWwGIyMjLi" +
            "MjQLZWZVy5zBYAAAAAAAAAAAAAAAAAAAAAAAAA";

        byte[] aggsBytes = Base64.getDecoder().decode(aggsString);
        try (NamedWriteableAwareStreamInput in = new NamedWriteableAwareStreamInput(StreamInput.wrap(aggsBytes), registry)) {
            Version version = VersionUtils.randomVersionBetween(random(), Version.V_6_7_0.minimumCompatibilityVersion(),
                VersionUtils.getPreviousVersion(Version.V_6_7_0));
            in.setVersion(version);
            InternalAggregations deserialized = InternalAggregations.readAggregations(in);
            assertEquals(3, deserialized.aggregations.size());
            assertThat(deserialized.aggregations.get(0), Matchers.instanceOf(StringTerms.class));
            assertThat(deserialized.aggregations.get(1), Matchers.instanceOf(InternalDateHistogram.class));
            assertThat(deserialized.aggregations.get(2), Matchers.instanceOf(InternalSimpleValue.class));
        }
    }
}
