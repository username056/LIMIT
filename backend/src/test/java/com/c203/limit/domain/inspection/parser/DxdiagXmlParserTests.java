package com.c203.limit.domain.inspection.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DxdiagXmlParserTests {

    private final DxdiagXmlParser parser = new DxdiagXmlParser();

    @Test
    void parsesAllFieldsFromCompleteDxdiagXml() {
        DxdiagParseResult result = parser.parse(bytes(completeDxdiagXml()));

        assertThat(result.modelName()).isEqualTo("950XDB/951XDB/950XDY");
        assertThat(result.osVersion()).isEqualTo("Windows 10 Pro 64-bit (10.0, Build 19045)");
        assertThat(result.storageCapacity()).isEqualTo("475.8 GB");
        assertThat(result.cpu()).isEqualTo("11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz (8 CPUs), ~2.8GHz");
        assertThat(result.memory()).isEqualTo("16384 MB RAM");
        assertThat(result.gpu()).isEqualTo("Intel(R) Iris(R) Xe Graphics");
        assertThat(result.gpuMemory()).isEqualTo("8156 MB");
        assertThat(result.driverVersion()).isEqualTo("27.20.100.9415");
        assertThat(result.soundDevice()).isEqualTo("스피커(Realtek(R) Audio)");
        assertThat(result.isComplete()).isTrue();
        assertThat(result.missingFields()).isEmpty();
    }

    @Test
    void picksSoundDeviceMarkedAsDefaultAmongMultiple() {
        String xml =
                """
                <DxDiag>
                  <SystemInformation>
                    <SystemModel>Test Model</SystemModel>
                    <OperatingSystem>Test OS</OperatingSystem>
                    <Processor>Test CPU</Processor>
                    <Memory>8192MB RAM</Memory>
                  </SystemInformation>
                  <DisplayDevices>
                    <DisplayDevice>
                      <CardName>Test GPU</CardName>
                      <DisplayMemory>4096 MB</DisplayMemory>
                      <DriverVersion>1.0.0.1</DriverVersion>
                    </DisplayDevice>
                  </DisplayDevices>
                  <SoundDevices>
                    <SoundDevice>
                      <Description>Not Default Speaker</Description>
                      <DefaultSoundPlayback>0</DefaultSoundPlayback>
                    </SoundDevice>
                    <SoundDevice>
                      <Description>Default Speaker</Description>
                      <DefaultSoundPlayback>1</DefaultSoundPlayback>
                    </SoundDevice>
                  </SoundDevices>
                  <LogicalDisks>
                    <LogicalDisk><TotalSpace>475.8 GB</TotalSpace></LogicalDisk>
                  </LogicalDisks>
                </DxDiag>
                """;

        DxdiagParseResult result = parser.parse(bytes(xml));

        assertThat(result.soundDevice()).isEqualTo("Default Speaker");
    }

    @Test
    void returnsPartialResultWhenDisplayDeviceSectionIsMissing() {
        String xml =
                """
                <DxDiag>
                  <SystemInformation>
                    <SystemModel>Test Model</SystemModel>
                    <OperatingSystem>Test OS</OperatingSystem>
                    <Processor>Test CPU</Processor>
                    <Memory>8192MB RAM</Memory>
                  </SystemInformation>
                  <SoundDevices>
                    <SoundDevice>
                      <Description>Default Speaker</Description>
                      <DefaultSoundPlayback>1</DefaultSoundPlayback>
                    </SoundDevice>
                  </SoundDevices>
                  <LogicalDisks>
                    <LogicalDisk><TotalSpace>475.8 GB</TotalSpace></LogicalDisk>
                  </LogicalDisks>
                </DxDiag>
                """;

        DxdiagParseResult result = parser.parse(bytes(xml));

        assertThat(result.gpu()).isNull();
        assertThat(result.gpuMemory()).isNull();
        assertThat(result.driverVersion()).isNull();
        assertThat(result.isComplete()).isFalse();
        assertThat(result.missingFields()).containsExactlyInAnyOrder("gpu", "gpuMemory", "driverVersion");
    }

    @Test
    void throwsWhenRootElementIsNotDxDiag() {
        assertThatThrownBy(() -> parser.parse(bytes("<NotDxDiag></NotDxDiag>")))
                .isInstanceOf(DxdiagParseException.class);
    }

    @Test
    void throwsWhenXmlIsMalformed() {
        assertThatThrownBy(() -> parser.parse(bytes("<DxDiag><Unclosed>")))
                .isInstanceOf(DxdiagParseException.class);
    }

    @Test
    void rejectsDoctypeDeclarationToPreventXxe() {
        String xxePayload =
                """
                <?xml version="1.0"?>
                <!DOCTYPE DxDiag [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <DxDiag>
                  <SystemInformation>
                    <Processor>&xxe;</Processor>
                  </SystemInformation>
                </DxDiag>
                """;

        assertThatThrownBy(() -> parser.parse(bytes(xxePayload)))
                .isInstanceOf(DxdiagParseException.class);
    }

    private byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private String completeDxdiagXml() {
        return """
                <DxDiag>
                  <SystemInformation>
                    <SystemManufacturer>SAMSUNG ELECTRONICS CO., LTD.</SystemManufacturer>
                    <SystemModel>950XDB/951XDB/950XDY</SystemModel>
                    <OperatingSystem>Windows 10 Pro 64-bit (10.0, Build 19045)</OperatingSystem>
                    <Processor>11th Gen Intel(R) Core(TM) i7-1165G7 @ 2.80GHz (8 CPUs), ~2.8GHz</Processor>
                    <Memory>16384MB RAM</Memory>
                  </SystemInformation>
                  <DisplayDevices>
                    <DisplayDevice>
                      <CardName>Intel(R) Iris(R) Xe Graphics</CardName>
                      <DisplayMemory>8156 MB</DisplayMemory>
                      <DedicatedMemory>128 MB</DedicatedMemory>
                      <SharedMemory>8028 MB</SharedMemory>
                      <DriverVersion>27.20.100.9415</DriverVersion>
                    </DisplayDevice>
                  </DisplayDevices>
                  <SoundDevices>
                    <SoundDevice>
                      <Description>스피커(Realtek(R) Audio)</Description>
                      <DefaultSoundPlayback>1</DefaultSoundPlayback>
                    </SoundDevice>
                    <SoundDevice>
                      <Description>스피커(Steam Streaming Speakers)</Description>
                      <DefaultSoundPlayback>0</DefaultSoundPlayback>
                    </SoundDevice>
                  </SoundDevices>
                  <LogicalDisks>
                    <LogicalDisk>
                      <TotalSpace>475.8GB</TotalSpace>
                    </LogicalDisk>
                  </LogicalDisks>
                </DxDiag>
                """;
    }
}
