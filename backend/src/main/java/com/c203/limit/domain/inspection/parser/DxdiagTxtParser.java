package com.c203.limit.domain.inspection.parser;

import com.c203.limit.domain.inspection.util.UnitNormalizer;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * DxDiag "모든 정보 저장"(Save All Information) 텍스트 출력을 정규식 기반 라인 파싱으로 읽는다. 텍스트는
 * {@code ------------------ / <섹션명> / ------------------} 구분선으로 System Information, Display
 * Devices, Sound Devices 섹션이 나뉘고, 각 섹션 안에서 {@code <라벨>: <값>} 형태의 줄이 이어진다.
 *
 * <p>한글 Windows에서 저장한 리포트는 BOM 없는 시스템 기본 코드페이지(CP949/MS949)로 저장되어 UTF-8로 읽으면
 * 한글 필드(예: 사운드 장치명)가 깨진다. BOM이 있으면 그에 따르고, 없으면 UTF-8로 엄격 디코딩을 시도한 뒤
 * 실패하면 MS949로 재시도한다.
 */
@Component
public class DxdiagTxtParser {

    private static final Charset MS949 = Charset.forName("MS949");

    private static final String SECTION_SYSTEM_INFORMATION = "System Information";
    private static final String SECTION_DISPLAY_DEVICES = "Display Devices";
    private static final String SECTION_SOUND_DEVICES = "Sound Devices";
    private static final String SECTION_DISK_DRIVES = "Disk & DVD/CD-ROM Drives";

    private static final Pattern SECTION_DELIMITER = Pattern.compile("^-{3,}$");
    private static final Pattern LABEL_VALUE = Pattern.compile("^\\s*([^:]+?)\\s*:\\s*(.*)$");

    public DxdiagParseResult parse(byte[] txtBytes) {
        try {
            String[] lines = decodeText(txtBytes).split("\r\n|\n|\r");

            String cpu = null;
            String memory = null;
            String modelName = null;
            String osVersion = null;
            String storageCapacity = null;
            String gpu = null;
            String gpuMemory = null;
            String driverVersion = null;
            List<String> soundDescriptions = new ArrayList<>();
            List<Boolean> soundIsDefault = new ArrayList<>();

            String currentSection = null;
            int i = 0;
            while (i < lines.length) {
                if (isDelimiter(lines[i]) && i + 2 < lines.length && isDelimiter(lines[i + 2])) {
                    currentSection = lines[i + 1].trim();
                    i += 3;
                    continue;
                }

                if (currentSection != null) {
                    Matcher matcher = LABEL_VALUE.matcher(lines[i]);
                    if (matcher.matches()) {
                        String label = matcher.group(1).trim();
                        String value = matcher.group(2).trim();
                        String nonBlankValue = value.isBlank() ? null : value;

                        switch (currentSection) {
                            case SECTION_SYSTEM_INFORMATION -> {
                                if (modelName == null && "System Model".equals(label)) {
                                    modelName = nonBlankValue;
                                }
                                if (osVersion == null && "Operating System".equals(label)) {
                                    osVersion = nonBlankValue;
                                }
                                if (cpu == null && "Processor".equals(label)) {
                                    cpu = nonBlankValue;
                                }
                                if (memory == null && "Memory".equals(label)) {
                                    memory = UnitNormalizer.normalizeUnitSpacing(nonBlankValue);
                                }
                            }
                            case SECTION_DISPLAY_DEVICES -> {
                                if (gpu == null && "Card name".equals(label)) {
                                    gpu = nonBlankValue;
                                }
                                if (gpuMemory == null && "Display Memory".equals(label)) {
                                    gpuMemory = UnitNormalizer.normalizeUnitSpacing(nonBlankValue);
                                }
                                if (driverVersion == null && "Driver Version".equals(label)) {
                                    driverVersion = nonBlankValue;
                                }
                            }
                            case SECTION_SOUND_DEVICES -> {
                                if ("Description".equals(label)) {
                                    soundDescriptions.add(nonBlankValue);
                                    soundIsDefault.add(Boolean.FALSE);
                                } else if ("Default Sound Playback".equals(label) && !soundDescriptions.isEmpty()) {
                                    soundIsDefault.set(soundIsDefault.size() - 1, "Yes".equalsIgnoreCase(value));
                                }
                            }
                            case SECTION_DISK_DRIVES -> {
                                if (storageCapacity == null && "Total Space".equals(label)) {
                                    storageCapacity = UnitNormalizer.normalizeUnitSpacing(nonBlankValue);
                                }
                            }
                            default -> {}
                        }
                    }
                }
                i++;
            }

            return new DxdiagParseResult(
                    modelName,
                    osVersion,
                    storageCapacity,
                    cpu,
                    memory,
                    gpu,
                    gpuMemory,
                    driverVersion,
                    resolveDefaultSoundDevice(soundDescriptions, soundIsDefault));
        } catch (Exception exception) {
            throw new DxdiagParseException("Failed to parse DxDiag.txt", exception);
        }
    }

    private String decodeText(byte[] bytes) {
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, 0xFF, 0xFE)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, 0xFE, 0xFF)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }

        CharsetDecoder strictUtf8 =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return strictUtf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            // 한글 Windows dxdiag 리포트는 BOM 없이 시스템 기본 코드페이지(CP949/MS949)로 저장된다.
            return new String(bytes, MS949);
        }
    }

    private boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private String resolveDefaultSoundDevice(List<String> descriptions, List<Boolean> isDefaultFlags) {
        for (int i = 0; i < descriptions.size(); i++) {
            if (Boolean.TRUE.equals(isDefaultFlags.get(i)) && descriptions.get(i) != null) {
                return descriptions.get(i);
            }
        }
        return descriptions.isEmpty() ? null : descriptions.get(0);
    }

    private boolean isDelimiter(String line) {
        return SECTION_DELIMITER.matcher(line.trim()).matches();
    }
}
