package com.c203.limit.domain.inspection.parser;

import com.c203.limit.domain.inspection.util.UnitNormalizer;
import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Windows dxdiag /x 출력 XML을 DOM으로 파싱해 CPU/메모리/GPU/드라이버/사운드 장치 정보를 추출한다. */
@Component
public class DxdiagXmlParser {

    private static final String ROOT_TAG = "DxDiag";

    public DxdiagParseResult parse(byte[] xmlBytes) {
        Element root = parseDocument(xmlBytes);

        Element systemInformation = firstElement(root, "SystemInformation");
        String modelName = text(systemInformation, "SystemModel");
        String osVersion = text(systemInformation, "OperatingSystem");
        String storageCapacity = extractStorageCapacity(root);
        String cpu = text(systemInformation, "Processor");
        String memory = UnitNormalizer.normalizeUnitSpacing(text(systemInformation, "Memory"));

        Element displayDevice = firstElement(root, "DisplayDevice");
        String gpu = text(displayDevice, "CardName");
        String gpuMemory = UnitNormalizer.normalizeUnitSpacing(text(displayDevice, "DisplayMemory"));
        String driverVersion = text(displayDevice, "DriverVersion");

        Element soundDevice = firstDefaultSoundDevice(root);
        String soundDeviceName = text(soundDevice, "Description");

        return new DxdiagParseResult(
                modelName, osVersion, storageCapacity, cpu, memory, gpu, gpuMemory, driverVersion, soundDeviceName);
    }

    private String extractStorageCapacity(Element root) {
        Element logicalDisk = firstElement(root, "LogicalDisk");
        String value = text(logicalDisk, "TotalSpace");
        return UnitNormalizer.normalizeUnitSpacing(value);
    }

    private Element parseDocument(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE 방지: DOCTYPE 자체를 거부하고 외부 엔티티/DTD/스키마 접근을 모두 막는다.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xmlBytes));
            document.getDocumentElement().normalize();

            Element root = document.getDocumentElement();
            if (!ROOT_TAG.equals(root.getNodeName())) {
                throw new DxdiagParseException("Root element is not <DxDiag>: " + root.getNodeName());
            }
            return root;
        } catch (DxdiagParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DxdiagParseException("Failed to parse DxDiag.xml", exception);
        }
    }

    private Element firstElement(Element parent, String tagName) {
        if (parent == null) {
            return null;
        }
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private Element firstDefaultSoundDevice(Element root) {
        NodeList devices = root.getElementsByTagName("SoundDevice");
        for (int i = 0; i < devices.getLength(); i++) {
            Element device = (Element) devices.item(i);
            if ("1".equals(text(device, "DefaultSoundPlayback"))) {
                return device;
            }
        }
        return devices.getLength() > 0 ? (Element) devices.item(0) : null;
    }

    private String text(Element parent, String tagName) {
        Element element = firstElement(parent, tagName);
        if (element == null) {
            return null;
        }
        String value = element.getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
