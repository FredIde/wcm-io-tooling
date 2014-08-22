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
package io.wcm.testing.mock.osgi;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Helper methods to parse OSGi metadata.
 */
final class OsgiMetadataUtil {

  private static final DocumentBuilderFactory DOCUMENT_BUILDER_FACTORY;
  static {
    DOCUMENT_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();
    DOCUMENT_BUILDER_FACTORY.setNamespaceAware(true);
  }

  private static final XPathFactory XPATH_FACTORY = XPathFactory.newInstance();

  private static final BidiMap<String,String> NAMESPACES = new DualHashBidiMap<>();
  static {
    NAMESPACES.put("scr", "http://www.osgi.org/xmlns/scr/v1.1.0");
  }

  private OsgiMetadataUtil() {
    // static methods only
  }

  private static final NamespaceContext NAMESPACE_CONTEXT = new NamespaceContext() {
    @Override
    public String getNamespaceURI(String prefix) {
      return NAMESPACES.get(prefix);
    }
    @Override
    public String getPrefix(String namespaceURI) {
      return NAMESPACES.getKey(namespaceURI);
    }
    @Override
    public Iterator getPrefixes(String namespaceURI) {
      return NAMESPACES.keySet().iterator();
    }
  };

  /**
   * Try to read OSGI-metadata from /OSGI-INF and read all implemented interfaces and service properties
   * @param clazz OSGi service implementation class
   * @return Metadata document or null
   */
  public static Document geDocument(Class clazz) {
    String metadataPath = "/OSGI-INF/" + clazz.getName() + ".xml";
    InputStream metadataStream = clazz.getResourceAsStream(metadataPath);
    if (metadataStream == null) {
      return null;
    }
    try {
      DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
      return documentBuilder.parse(metadataStream);
    }
    catch (ParserConfigurationException | SAXException | IOException ex) {
      throw new RuntimeException("Unable to read classpath resource: " + metadataPath, ex);
    }
    finally {
      try {
        metadataStream.close();
      }
      catch (IOException ex) {
        // ignore
      }
    }
  }

  public static Set<String> getServiceInterfaces(Document document) {
    Set<String> serviceInterfaces = new HashSet<>();

    if (document != null) {
      try {
        XPath xpath = XPATH_FACTORY.newXPath();
        xpath.setNamespaceContext(NAMESPACE_CONTEXT);
        NodeList nodes = (NodeList)xpath.evaluate("/components/component[1]/service/provide[@interface!='']", document, XPathConstants.NODESET);
        if (nodes != null) {
          for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String serviceInterface = node.getAttributes().getNamedItem("interface").getNodeValue();
            if (StringUtils.isNotBlank(serviceInterface)) {
              serviceInterfaces.add(serviceInterface);
            }
          }
        }
      }
      catch (XPathExpressionException ex) {
        throw new RuntimeException("Error evaluating XPath.", ex);
      }
    }

    return serviceInterfaces;
  }

  public static Map<String, Object> getProperties(Document document) {
    Map<String, Object> props = new HashMap<>();

    if (document != null) {
      try {
        XPath xpath = XPATH_FACTORY.newXPath();
        xpath.setNamespaceContext(NAMESPACE_CONTEXT);
        NodeList nodes = (NodeList)xpath.evaluate("/components/component[1]/property[@name!='' and @value!='']", document, XPathConstants.NODESET);
        if (nodes != null) {
          for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String name = node.getAttributes().getNamedItem("name").getNodeValue();
            String value = node.getAttributes().getNamedItem("value").getNodeValue();
            String type = null;
            Node typeAttribute = node.getAttributes().getNamedItem("type");
            if (typeAttribute != null) {
              type = typeAttribute.getNodeValue();
            }
            if (StringUtils.equals("Integer", type)) {
              props.put(name, Integer.parseInt(value));
            }
            else {
              props.put(name, value);
            }
          }
        }
      }
      catch (XPathExpressionException ex) {
        throw new RuntimeException("Error evaluating XPath.", ex);
      }
    }

    return props;
  }

}
