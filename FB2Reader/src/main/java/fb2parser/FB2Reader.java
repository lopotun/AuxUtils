package fb2parser;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Evgeny Kurtser on 28-Aug-22 at 10:59 PM.
 * <a href=mailto:EvgenyK@traiana.com>EvgenyK@traiana.com</a>
 */
public class FB2Reader {
	private static final Logger log = LoggerFactory.getLogger(FB2Reader.class);
	private static final SortedMap<String, String> refsMap = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
	private static final Pattern PATTERN_REF_POINTER = Pattern.compile("([\\D]+)(\\d+)");
	private static final Pattern PATTERN_REF_DEST = Pattern.compile("^(\\d+)\\s+(.+)");

	public static void main(String[] args) {
		try {
//			Document xmlDocument = unmarshallXPath("src/main/resources/fb2-master/putinburg.fb2.xml");
//			addNotes(xmlDocument);
//			saveXMLContent(xmlDocument, "src/main/resources/fb2-master/putinburg.fb2_NEW.xml");
			testNotesSequence("src/main/resources/fb2-master/putinburg.fb2_NEW.xml");
		} catch(ParserConfigurationException | IOException | SAXException e) {
			e.printStackTrace();
		} catch(XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	private static void testNotesSequence(String fileName) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {
		FileInputStream fileIS = new FileInputStream(fileName);
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = builderFactory.newDocumentBuilder();
		Document xmlDocument = builder.parse(fileIS);
		XPath xPath = XPathFactory.newInstance().newXPath();
		String expression = "/FictionBook/body/section[position()>3]/p/a";
		NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);

		Pattern patternNotesRef = Pattern.compile("\\[(\\d{1,3})]");
		int lastNotesRef = 0;
		for(int i = 0; i < nodeList.getLength(); i++) {
			final Node pNode = nodeList.item(i);
			final Node pNodeText = pNode.getFirstChild();
			final String pNodeTextValue = pNodeText.getNodeValue();
			if(pNodeTextValue != null) {
				final Matcher matcher = patternNotesRef.matcher(pNodeTextValue);
				while(matcher.find()) {
					int currentNotesRef = Integer.parseInt(matcher.group(1));
					if(currentNotesRef - lastNotesRef != 1) {
						System.out.printf("%d -> %d in line %s", lastNotesRef, currentNotesRef, pNode.getPreviousSibling().getNodeValue());
					} else {
						lastNotesRef = currentNotesRef;
					}
				}
			}
		}
	}

	private static Document unmarshallXPath(String fileName) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {
		FileInputStream fileIS = new FileInputStream(fileName);
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = builderFactory.newDocumentBuilder();
		Document xmlDocument = builder.parse(fileIS);
		XPath xPath = XPathFactory.newInstance().newXPath();
		String expression = "/FictionBook/body/section[position()>3]/p[text()]";
		NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(xmlDocument, XPathConstants.NODESET);

		for(int i = 0; i < nodeList.getLength(); i++) {
			Node pNode = nodeList.item(i);
			Node pNodeText = pNode.getFirstChild();
			final String pNodeTextValue = pNodeText.getNodeValue();
			if(pNodeTextValue != null) {
				final Matcher matcher = PATTERN_REF_DEST.matcher(pNodeTextValue);
				if(matcher.matches()) {
					refsMap.put(matcher.group(1), matcher.group(2));
					Node removedNode = pNode.getParentNode().removeChild(pNode);
					log.debug("Node {} has been removed", removedNode.toString());
				}
			}
		}


		Scanner scanner = new Scanner(System.in);
		boolean autoContinue = false;
		for(int i = 0; i < nodeList.getLength(); i++) {
			Text pNode = (Text)nodeList.item(i).getFirstChild();
			final String pNodeValue = pNode.getNodeValue();
			if(pNodeValue != null) {
				Matcher matcher = PATTERN_REF_POINTER.matcher(pNodeValue);
				int lastEnd = 0;
				StringBuilder sb = new StringBuilder(pNodeValue.length());
				while(matcher.find()) {
					lastEnd = matcher.end();
					String beforeNumber = matcher.group(1);
					String number = matcher.group(2);
					if(!number.startsWith("0") && !beforeNumber.endsWith(" ")) {
						String ref = refsMap.get(number);
						if(ref != null) {
							String context = getLastN(beforeNumber, 60);
							log.debug(String.format("%s\t will go with %s. Accept?y/[N]/a%n", context, ref));
							String response = "n";
							if(!autoContinue) {
								response = scanner.nextLine();
							}
							if(autoContinue || response.equalsIgnoreCase("y")) {
								// <a l:href="#n_1" type="note">[1]</a>
								sb.append(matcher.group(1))
										.append("<a l:href=\"#n_")
										.append(number)
										.append("\" type=\"note\">[")
										.append(number)
										.append("]</a>");
							} else if(response.equalsIgnoreCase("a")) {
								// <a l:href="#n_1" type="note">[1]</a>
								sb.append(matcher.group(1))
										.append("<a l:href=\"#n_")
										.append(number)
										.append("\" type=\"note\">[")
										.append(number)
										.append("]</a>");
								autoContinue = true;

							}
						}
					} else {
						sb.append(matcher.group());
					}
				}
				if(lastEnd > 0) {
					sb.append(pNodeValue.substring(lastEnd));
					pNode.setNodeValue(sb.toString());
				}
			}
		}
		return xmlDocument;
	}

	private static void addNotes(Document xmlDocument) {
		/*
		<body name="notes">
            <title>
                <p>Примечания</p>
            </title>
        </body>
		 */
		try {
			Node rootNode = xmlDocument.getDocumentElement();
			Element bodyNode = xmlDocument.createElement("body");
			bodyNode.setAttribute("name", "notes");
			rootNode.appendChild(bodyNode);

			Element titleNodeHeader = xmlDocument.createElement("title");
			bodyNode.appendChild(titleNodeHeader);

			Element pNodeHeader = xmlDocument.createElement("p");
			titleNodeHeader.appendChild(pNodeHeader);

			Text textNodeHeader = xmlDocument.createTextNode("Примечания");
			pNodeHeader.appendChild(textNodeHeader);

			/*
			<section id="n_1">
			   <title>
			     <p>1</p>
			   </title>
			   <p>Часть Калининского района Петербурга, названная так из-за своей центральной магистрали — Гражданского проспекта.</p>
			</section>
			 */
			refsMap.forEach((key, value) -> {
				Element sectionNode = xmlDocument.createElement("section");
				sectionNode.setAttribute("id", "n_"+key);
				bodyNode.appendChild(sectionNode);

				Element titleNode = xmlDocument.createElement("title");
				sectionNode.appendChild(titleNode);

				Element pNodeKey = xmlDocument.createElement("p");
				titleNode.appendChild(pNodeKey);

				Text textNodeKey = xmlDocument.createTextNode(key);
				pNodeKey.appendChild(textNodeKey);

				Element pNodeValue = xmlDocument.createElement("p");
				sectionNode.appendChild(pNodeValue);

				Text textNodeValue = xmlDocument.createTextNode(value);
				pNodeValue.appendChild(textNodeValue);
			});
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
	}

	private static void saveXMLContent(Document document, String xmlFile) {
		try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "no");
			transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
			DOMSource domSource = new DOMSource(document);
			StreamResult streamResult = new StreamResult(xmlFile);
			transformer.transform(domSource, streamResult);
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
	}


	private static String getLastN(String myString, int lastN) {
		if(myString.length() > lastN)
			return myString.substring(myString.length() - lastN);
		else
			return myString;
	}
}
