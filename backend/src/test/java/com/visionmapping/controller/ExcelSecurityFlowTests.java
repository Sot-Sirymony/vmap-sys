package com.visionmapping.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * V10 phase 4: spreadsheet formula injection on export (SEC-05-04) and safe,
 * bounded imports (SEC-05-05, SEC-08-01, SEC-08-02).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.excel.backup-dir=target/test-excel-backups")
class ExcelSecurityFlowTests {

    private static final String FORMULA_NAME = "=HYPERLINK(\"http://example.com\",\"Click\")";
    private static final int NAME_COLUMN = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ---------- SEC-05-04: export ----------

    @Test
    void aValueThatLooksLikeAFormulaIsExportedAsTextThatStaysText() throws Exception {
        String token = register();
        createArea(token, FORMULA_NAME);
        createArea(token, "Career");

        try (Workbook workbook = read(export(token))) {
            Cell formulaLike = nameCell(workbook, FORMULA_NAME);
            assertThat(formulaLike.getCellType()).isEqualTo(CellType.STRING);
            assertThat(formulaLike.getCellStyle().getQuotePrefixed()).isTrue();
            assertThat(nameCell(workbook, "Career").getCellStyle().getQuotePrefixed()).isFalse();
        }
    }

    @Test
    void theEscapedValueImportsBackExactlyAsWritten() throws Exception {
        String owner = register();
        createArea(owner, FORMULA_NAME);
        byte[] workbook = export(owner);

        String importer = register();
        JsonNode summary = importFile(importer, "export.xlsx", workbook);

        assertThat(summary.get("validationErrors")).isEmpty();
        assertThat(areaNames(importer)).contains(FORMULA_NAME);
    }

    // ---------- SEC-05-05, SEC-08-02: what may be imported ----------

    @Test
    void onlyXlsxFilesAreAccepted() throws Exception {
        String token = register();
        JsonNode summary = importFile(token, "macros.xlsm", export(token));
        assertThat(summary.get("validationErrors").get(0).asText()).isEqualTo("Only .xlsx workbooks can be imported.");
    }

    @Test
    void aMacroEnabledWorkbookIsRefusedEvenWhenNamedXlsx() throws Exception {
        String token = register();
        byte[] macroWorkbook;
        try (XSSFWorkbook workbook = new XSSFWorkbook(XSSFWorkbookType.XLSM)) {
            workbook.createSheet("Vision Areas");
            macroWorkbook = write(workbook);
        }

        JsonNode summary = importFile(token, "renamed.xlsx", macroWorkbook);

        assertThat(summary.get("validationErrors").get(0).asText()).startsWith("Workbooks with macros cannot be imported.");
        assertThat(summary.get("createdRecords").asInt()).isZero();
    }

    @Test
    void aFileThatIsNotAWorkbookGetsAClearAnswerNotAServerError() throws Exception {
        String token = register();
        JsonNode summary = importFile(token, "fake.xlsx", "this is not a spreadsheet".getBytes());
        assertThat(summary.get("validationErrors").get(0).asText()).isEqualTo("This file is not a readable .xlsx workbook.");
    }

    @Test
    void aSheetWithTooManyRowsIsRefused() throws Exception {
        String token = register();
        byte[] big;
        try (Workbook workbook = read(export(token))) {
            Sheet areas = workbook.getSheet("Vision Areas");
            areas.createRow(5_001).createCell(NAME_COLUMN).setCellValue("Far away");
            big = write(workbook);
        }

        JsonNode summary = importFile(token, "big.xlsx", big);

        assertThat(summary.get("validationErrors").get(0).asText()).isEqualTo("The sheet \"Vision Areas\" has more than 5000 rows.");
        assertThat(summary.get("createdRecords").asInt()).isZero();
    }

    @Test
    void aFormulaIsReadAsTheValueItLastShowedNeverAsItsText() throws Exception {
        String owner = register();
        createArea(owner, "Placeholder");
        byte[] edited;
        try (Workbook workbook = read(export(owner))) {
            Cell name = nameCell(workbook, "Placeholder");
            name.setCellFormula("WEBSERVICE(\"http://example.com/steal\")");
            name.setCellValue("Shown value");
            edited = write(workbook);
        }

        String importer = register();
        importFile(importer, "formula.xlsx", edited);

        assertThat(areaNames(importer)).containsExactly("Shown value");
    }

    @Test
    void aRowThatFailsTheFormRulesIsSkippedWithTheReasonAndTheRestImport() throws Exception {
        String owner = register();
        createArea(owner, "Too long soon");
        createArea(owner, "Fine");
        byte[] edited;
        try (Workbook workbook = read(export(owner))) {
            nameCell(workbook, "Too long soon").setCellValue("x".repeat(161));
            edited = write(workbook);
        }

        String importer = register();
        JsonNode summary = importFile(importer, "long.xlsx", edited);

        assertThat(summary.get("validationErrors").toString()).contains("name: size must be between");
        assertThat(areaNames(importer)).containsExactly("Fine");
    }

    // ---------- helpers ----------

    private String register() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Excel User",
                                "email", "excel-" + UUID.randomUUID() + "@example.com", "password", "Steady-Harbor-Lamp-42"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private void createArea(String token, String name) throws Exception {
        mockMvc.perform(post("/api/vision-areas").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "priority", "HIGH", "status", "ACTIVE"))))
                .andExpect(status().isCreated());
    }

    private List<String> areaNames(String token) throws Exception {
        JsonNode areas = objectMapper.readTree(mockMvc.perform(get("/api/vision-areas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> names = new ArrayList<>();
        (areas.has("content") ? areas.get("content") : areas).forEach(area -> names.add(area.get("name").asText()));
        return names;
    }

    private byte[] export(String token) throws Exception {
        return mockMvc.perform(post("/api/excel/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private JsonNode importFile(String token, String fileName, byte[] content) throws Exception {
        MockMultipartFile upload = new MockMultipartFile("file", fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
        String body = mockMvc.perform(multipart("/api/excel/import").file(upload).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static Cell nameCell(Workbook workbook, String name) {
        for (Row row : workbook.getSheet("Vision Areas")) {
            Cell cell = row.getCell(NAME_COLUMN);
            if (cell != null && cell.getCellType() == CellType.STRING && name.equals(cell.getStringCellValue())) {
                return cell;
            }
        }
        throw new AssertionError("No vision area named " + name + " in the export");
    }

    private static Workbook read(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static byte[] write(Workbook workbook) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
