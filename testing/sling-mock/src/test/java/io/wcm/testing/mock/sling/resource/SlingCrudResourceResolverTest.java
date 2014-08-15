/*
 * #%L
 * wcm.io
 * %%
 * Copyright (C) 2014 wcm.io
 * %%
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
 * #L%
 */
package io.wcm.testing.mock.sling.resource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import io.wcm.testing.junit.rules.parameterized.Generator;
import io.wcm.testing.junit.rules.parameterized.GeneratorFactory;
import io.wcm.testing.mock.sling.MockSlingFactory;
import io.wcm.testing.mock.sling.ResourceResolverType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.io.IOUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.testing.jcr.RepositoryUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Implements simple write and read resource and values test.
 * Sling CRUD API is used to create the test data.
 */
@SuppressWarnings("javadoc")
public class SlingCrudResourceResolverTest {

  //CHECKSTYLE:OFF
  // Run all unit tests for each resource resolver typ listed here
  @Rule
  public final Generator<ResourceResolverType> resourceResolverType = GeneratorFactory.list(
      ResourceResolverType.JCR_MOCK,
      ResourceResolverType.JCR_JACKRABBIT,
      ResourceResolverType.RESOURCERESOLVER_MOCK
      );
  //CHECKSTYLE:ON

  private static final String STRING_VALUE = "value1";
  private static final String[] STRING_ARRAY_VALUE = new String[] {
    "value1", "value2"
  };
  private static final int INTEGER_VALUE = 25;
  private static final double DOUBLE_VALUE = 3.555d;
  private static final boolean BOOLEAN_VALUE = true;
  private static final Date DATE_VALUE = new Date(10000);
  private static final Calendar CALENDAR_VALUE = Calendar.getInstance();
  private static final byte[] BINARY_VALUE = new byte[] {
    0x01, 0x02, 0x03, 0x04, 0x05, 0x06
  };

  private ResourceResolver resourceResolver;
  protected Resource testRoot;
  private static volatile long rootNodeCounter;

  @Before
  public void setUp() throws RepositoryException, IOException {
    this.resourceResolver = MockSlingFactory.newResourceResolver(this.resourceResolverType.value());

    if (this.resourceResolverType.value() == ResourceResolverType.JCR_JACKRABBIT) {
      Session session = this.resourceResolver.adaptTo(Session.class);
      RepositoryUtil.registerSlingNodeTypes(session);
    }

    // prepare some test data using Sling CRUD API
    Resource rootNode = getTestRootResource();

    Map<String, Object> props = new HashMap<>();
    props.put("stringProp", STRING_VALUE);
    props.put("stringArrayProp", STRING_ARRAY_VALUE);
    props.put("integerProp", INTEGER_VALUE);
    props.put("doubleProp", DOUBLE_VALUE);
    props.put("booleanProp", BOOLEAN_VALUE);
    props.put("dateProp", DATE_VALUE);
    props.put("calendarProp", CALENDAR_VALUE);
    props.put("binaryProp", new ByteArrayInputStream(BINARY_VALUE));
    Resource node1 = this.resourceResolver.create(rootNode, "node1", props);

    this.resourceResolver.create(node1, "node11", ValueMap.EMPTY);
    this.resourceResolver.create(node1, "node12", ValueMap.EMPTY);

    this.resourceResolver.commit();
  }

  @After
  public void tearDown() {
    this.testRoot = null;
  }

  /**
   * Return a test root resource, created on demand, with a unique path
   * @throws PersistenceException
   */
  private Resource getTestRootResource() throws PersistenceException {
    if (this.testRoot == null) {
      final Resource root = this.resourceResolver.getResource("/");
      if (this.resourceResolverType.value() == ResourceResolverType.JCR_JACKRABBIT) {
        final Resource classRoot = this.resourceResolver.create(root, getClass().getSimpleName(), ValueMap.EMPTY);
        this.testRoot = this.resourceResolver.create(classRoot, System.currentTimeMillis() + "_" + (rootNodeCounter++), ValueMap.EMPTY);
      }
      else {
        this.testRoot = this.resourceResolver.create(root, "test", ValueMap.EMPTY);
      }
    }
    return this.testRoot;
  }

  @Test
  public void testGetResourcesAndValues() throws IOException {
    Resource resource1 = this.resourceResolver.getResource(getTestRootResource().getPath() + "/node1");
    assertNotNull(resource1);
    assertEquals("node1", resource1.getName());

    ValueMap props = resource1.getValueMap();
    assertEquals(STRING_VALUE, props.get("stringProp", String.class));
    assertArrayEquals(STRING_ARRAY_VALUE, props.get("stringArrayProp", String[].class));
    assertEquals((Integer)INTEGER_VALUE, props.get("integerProp", Integer.class));
    assertEquals(DOUBLE_VALUE, props.get("doubleProp", Double.class), 0.0001);
    assertEquals(BOOLEAN_VALUE, props.get("booleanProp", Boolean.class));
    // TODO: enable this test when JCR resource implementation supports writing Date objects (SLING-3846)
    //assertEquals(DATE_VALUE, props.get("dateProp", Date.class));
    assertEquals(CALENDAR_VALUE.getTime(), props.get("calendarProp", Calendar.class).getTime());

    // TODO: enable this tests when resource resolver mock supports binary data
    if (this.resourceResolverType.value() != ResourceResolverType.RESOURCERESOLVER_MOCK) {
      Resource binaryPropResource = resource1.getChild("binaryProp");
      InputStream is = binaryPropResource.adaptTo(InputStream.class);
      byte[] dataFromResource = IOUtils.toByteArray(is);
      is.close();
      assertArrayEquals(BINARY_VALUE, dataFromResource);

      // read second time to ensure not the original input stream was returned
      InputStream is2 = binaryPropResource.adaptTo(InputStream.class);
      byte[] dataFromResource2 = IOUtils.toByteArray(is2);
      is2.close();
      assertArrayEquals(BINARY_VALUE, dataFromResource2);
    }

    List<Resource> children = IteratorUtils.toList(resource1.listChildren());
    assertEquals(2, children.size());
    // TODO: enable this tests when resource resolver mock preserves child ordering (SLING-3847)
    if (this.resourceResolverType.value() != ResourceResolverType.RESOURCERESOLVER_MOCK) {
      assertEquals("node11", children.get(0).getName());
      assertEquals("node12", children.get(1).getName());
    }
  }

}
