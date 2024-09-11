/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MarkDownDependencyCreator {
  private static final UrlValidator URL_VALIDATOR = new UrlValidator();

  private static final Map<String, String> LICENSE_TO_URL_MAP = new HashMap<>();

  public static void main(final String[] args) {
    if (args.length < 1) {
      System.err.println("Please provide a valid path to the backend license xml file!");
      return;
    }
    final String licenseFilePath = args[0];
    try {
      final File inputFile = new File(licenseFilePath);
      final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      dbFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      final Document doc = dBuilder.parse(inputFile);
      doc.getDocumentElement().normalize();
      final NodeList nList = doc.getElementsByTagName("dependency");
      final StringBuilder dependencyMarkdownPage = new StringBuilder();
      dependencyMarkdownPage.append(createMarkdownHeader());

      for (int temp = 0; temp < nList.getLength(); temp++) {
        final Node nNode = nList.item(temp);
        if (nNode.getNodeType() == Node.ELEMENT_NODE) {
          final Element eElement = (Element) nNode;
          final OptimizeDependency licenseLink = new OptimizeDependency();
          licenseLink.setProjectName(getElementTextContent(eElement, "artifactId"));
          licenseLink.setProjectVersion(getElementTextContent(eElement, "version"));
          final String licenseName = getElementTextContent(eElement, "name");
          if (StringUtils.isNotBlank(licenseName)) {
            licenseLink.setLicenseName(licenseName);
            licenseLink.setLicenseLink(resolveLicenseUrl(eElement, licenseName));
          }
          if (licenseLink.isProperLicense()) {
            dependencyMarkdownPage.append(licenseLink.toMarkDown());
          } else {
            System.err.println(
                "Could not resolve valid license entry for :" + licenseLink.toMarkDown());
          }
        }
      }
      createMarkdownFile(dependencyMarkdownPage.toString());
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  private static String resolveLicenseUrl(final Element eElement, final String licenseName) {
    String licenseUrl = getElementTextContent(eElement, "url");
    if (URL_VALIDATOR.isValid(licenseUrl)) {
      LICENSE_TO_URL_MAP.put(licenseName, licenseUrl);
    } else {
      licenseUrl = LICENSE_TO_URL_MAP.get(licenseName);
    }
    return licenseUrl;
  }

  private static void createMarkdownFile(final String markdownPageAsString)
      throws FileNotFoundException {
    final PrintWriter out = new PrintWriter("./backend-dependencies.md");
    out.println(markdownPageAsString);
    out.flush();
    out.close();
  }

  private static String createMarkdownHeader() {
    return "---\n"
        + "\n"
        + "title: 'Back-end dependencies'\n"
        + "weight: 70\n"
        + "\n"
        + "menu:\n"
        + "  main:\n"
        + "    identifier: \"technical-guide-back-end-third-party-libraries\"\n"
        + "    parent: \"technical-guide-third-party-libraries\"\n"
        + "\n"
        + "---\n"
        + "\n";
  }

  private static String getElementTextContent(final Element eElement, final String tagName) {
    final NodeList nodeList = eElement.getElementsByTagName(tagName);
    if (nodeList != null && nodeList.getLength() > 0) {
      return nodeList.item(0).getTextContent();
    } else {
      return "";
    }
  }
}
