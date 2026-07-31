package com.ourgiant.docscrubber.parser;

import com.ourgiant.docscrubber.model.Channel;
import com.ourgiant.docscrubber.model.DocumentFormat;
import com.ourgiant.docscrubber.model.ExtractionModel;
import com.ourgiant.docscrubber.model.SourceLocation;
import com.ourgiant.docscrubber.model.TextFragment;
import com.ourgiant.docscrubber.model.VisibilityAttributes;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extracts element text content and attribute values in a .xml file as
 * {@link com.ourgiant.docscrubber.model.Channel#BODY} fragments, and {@code <!-- -->} comments as
 * {@link Channel#COMMENT}. There is no hidden-text concept in XML, so every fragment gets the same
 * neutral visibility — the same pattern {@link PlainTextParser} uses for the same reason.
 *
 * <p><b>XXE hardening:</b> this parser scans untrusted, potentially adversarial input, so DOCTYPE
 * declarations are rejected outright and external entity resolution is disabled — otherwise a
 * malicious document could trigger external entity injection (XXE), reading local files or making
 * outbound requests during what is supposed to be a purely offline scan.</p>
 */
public final class XmlParser implements DocumentParser {

    @Override
    public boolean supports(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    @Override
    public ExtractionModel parse(Path path) throws IOException {
        List<TextFragment> fragments = new ArrayList<>();
        try {
            Document document = newHardenedDocumentBuilder().parse(path.toFile());
            document.getDocumentElement().normalize();
            walk(document.getDocumentElement(), "", fragments);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Failed to parse XML: " + e.getMessage(), e);
        }
        return new ExtractionModel(path, DocumentFormat.XML, fragments, List.of());
    }

    /** OWASP-recommended XXE-hardening recipe: reject DOCTYPE outright (blocks XXE and entity-expansion attacks in one step) and disable external entity/DTD resolution as defense in depth. */
    private DocumentBuilder newHardenedDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private void walk(Element element, String path, List<TextFragment> out) {
        String elementPath = path + "/" + element.getTagName();

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            addFragment(attr.getValue(), Channel.BODY, elementPath + "/@" + attr.getName(), out);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE -> walk((Element) child, elementPath, out);
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> addFragment(child.getNodeValue(), Channel.BODY, elementPath, out);
                case Node.COMMENT_NODE -> addFragment(child.getNodeValue(), Channel.COMMENT, elementPath + "/#comment", out);
                default -> {
                    // processing instructions, entity references, etc. -- no text to extract
                }
            }
        }
    }

    private void addFragment(String text, Channel channel, String path, List<TextFragment> out) {
        if (text != null && !text.isBlank()) {
            out.add(new TextFragment(text.strip(), channel, SourceLocation.field(path), VisibilityAttributes.builder().build()));
        }
    }
}
