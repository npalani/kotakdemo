/*
 *  Copyright 2015 Adobe Systems Incorporated
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.kotakdemo.core.servlets;

import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.json.CDL;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.jcr.AccessDeniedException;
import javax.jcr.InvalidItemStateException;
import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.version.VersionException;
import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.FactoryConfigurationError;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

/**
 * Servlet that writes some sample content into the response. It is mounted for
 * all resources of a specific Sling resource type. The
 * {@link SlingSafeMethodsServlet} shall be used for HTTP methods that are
 * idempotent. For write operations use the {@link SlingAllMethodsServlet}.
 */
@SuppressWarnings("serial")
@SlingServlet(paths = "/bin/testconnection", methods = "POST")
public class KotakDemoServlet extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(KotakDemoServlet.class);
    private static final String NEW_LINE_SEPARATOR = "\n";


    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException,
            IOException {
        createExcel(request);
    }
    private void createExcel(SlingHttpServletRequest request) {
        try {
            ResourceResolver resourceResolver = request.getResourceResolver();
            final RequestParameterMap requestParameterMap = request.getRequestParameterMap();
            LOGGER.debug("map size", requestParameterMap.size());
            final RequestParameter[] dataXmls = requestParameterMap.get("dataXml");
            final InputStream inputStream = dataXmls[0].getInputStream();
            final String formString = IOUtils.toString(inputStream);
            LOGGER.debug("Form string ::", formString);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource( new StringReader( formString ) ));
            Element documentElement = doc.getDocumentElement();
            NodeList data = documentElement.getElementsByTagName("data");
            NodeList childNodes = data.item(0).getChildNodes();
            JSONObject jsonObject = new JSONObject();
            for (int i=0;i<childNodes.getLength();i++) {
                String textContent = childNodes.item(i).getTextContent();
                String nodeName = childNodes.item(i).getNodeName();
                LOGGER.info("nodeName ::" + nodeName +" :: " + "textContent" + ":: "+textContent );
                if (!nodeName.equals("#text")) {
                    jsonObject.put(nodeName,textContent);
                }
            }

            org.apache.sling.commons.json.JSONArray jsonArray = new org.apache.sling.commons.json.JSONArray();
            final Node parentNode = resourceResolver.resolve("/etc/designs/kotakdemo/clientlib-all/lead.csv")
                    .adaptTo(Node.class);
            if (null != parentNode) {
                InputStream inputStream1 = JcrUtils.readFile(parentNode);
                String fileString = IOUtils.toString(inputStream1);
                JSONArray objects = CDL.toJSONArray(fileString);
                for (int j=0;j<objects.length(); j++) {
                    String newString = objects.get(j).toString();
                    JSONObject json = new JSONObject(newString);
                    jsonArray.put(json);
                }
            }

            jsonArray.put(jsonObject);
            String csvString = CDL.toString(new JSONArray(jsonArray.toString()));
            LOGGER.info("csvString", csvString);
            saveData(resourceResolver, csvString);
            LOGGER.info("jsonObject ::", jsonObject);

        } catch (IOException e) {
            LOGGER.error("IOException ::", e);
        } catch (FactoryConfigurationError factoryConfigurationError) {
            LOGGER.error("factoryConfigurationError ::", factoryConfigurationError);
        } catch (ParserConfigurationException e) {
            LOGGER.error("ParserConfigurationException ::", e);
        } catch (SAXException e) {
            LOGGER.error("SAXException ::", e);
        } catch (JSONException e) {
            LOGGER.error("JSONException ::", e);
        } catch (ReferentialIntegrityException e) {
            e.printStackTrace();
        } catch (InvalidItemStateException e) {
            e.printStackTrace();
        } catch (NoSuchNodeTypeException e) {
            e.printStackTrace();
        } catch (LockException e) {
            e.printStackTrace();
        } catch (VersionException e) {
            e.printStackTrace();
        } catch (AccessDeniedException e) {
            e.printStackTrace();
        } catch (ConstraintViolationException e) {
            e.printStackTrace();
        } catch (ItemExistsException e) {
            e.printStackTrace();
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
    }

    private void saveData(ResourceResolver resourceResolver, String csvString) throws RepositoryException {
        final Node parentNode = resourceResolver.resolve("/etc/designs/kotakdemo/clientlib-all")
                .adaptTo(Node.class);
        final InputStream comapnyDeatilsInputStreamUpdate = new ByteArrayInputStream(csvString.getBytes());
        JcrUtils.putFile(parentNode, "lead.csv", "application/json", comapnyDeatilsInputStreamUpdate);

        Session session = resourceResolver.adaptTo(Session.class);
        session.save();
    }
}
