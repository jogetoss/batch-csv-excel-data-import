package org.joget.marketplace.dataimport.batchimport;

import au.com.bytecode.opencsv.CSVReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.FormDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDaoImpl;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormDataDeletableBinder;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.service.FormUtil;
import org.joget.apps.userview.model.PwaOfflineNotSupported;
import org.joget.apps.userview.model.UserviewMenu;
import org.joget.commons.util.FileLimitException;
import org.joget.commons.util.FileStore;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.commons.util.ResourceBundleUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.util.WorkflowUtil;
import org.mozilla.universalchardet.Constants;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.beans.BeansException;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class BatchImportMenu extends UserviewMenu implements PwaOfflineNotSupported {
    public static String MODE_NEW = "NEW";
    public static String MODE_NEW_UPDATE = "NEW & UPDATE";
    public static String MODE_DELETE = "DELETE";
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int MAX_BATCH_SIZE = 50000;
    private static final int CSV_DELIMITER_SAMPLE_LINES = 20;
    private static final String LOG_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    
    private FormService formService;
    private final BatchImportDao batchImportDao = new BatchImportDaoImpl();
    private final BatchImportJobHelper jobHelper = new BatchImportJobHelper();
    
    private Form form;
    private Set<String> importFields;

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        return "Batch Import Menu";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-file-import\"></i>";
    }

    @Override
    public String getName() {
        return "Batch Import Menu";
    }

    @Override
    public String getVersion() {
        return "5.0.0";
    }

    @Override
    public String getDescription() {
        return "The Batch Import Menu enables end user to import bulk data into a specific form and doing a simple column-to-field mapping.";
    }

    @Override
    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion};
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/userview/batchImportMenu.json", arguments, true, "messages/batchImportMenu");
        return json;
    }

    @Override
    public String getDecoratedMenu() {
        return null;
    }

    @Override
    public boolean isHomePageSupported() {
        return true;
    }

    @Override
    public String getCategory() {
        return "Marketplace";
    }

    @Override
    public String getRenderPage() {
        String action = getRequestParameterString("action");

        String url = getUrl() + "?action=submit";
        setProperty("url", url);

        Map model = new HashMap();
        if ("status".equals(action)) {
            displayJobStatus(model);
        } else if ("submit".equals(action)) {
            // only allow POST
            HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
            if (request != null && !"POST".equalsIgnoreCase(request.getMethod())) {
                PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
                String content = pluginManager.getPluginFreeMarkerTemplate(new HashMap(), getClass().getName(), "/templates/unauthorized.ftl", "messages/batchImportMenu");
                return content;
            }

            handleSubmit(model);
        } else {
            displayForm();
        }
        
        model.put("request", getRequestParameters());
        model.put("element", this);
        
        PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
        String content = pluginManager.getPluginFreeMarkerTemplate(model, getClass().getName(), "/templates/batchImport.ftl", "messages/batchImportMenu");
        return content;
    }
    
    protected void displayForm() {
        form = getForm();
        if (form == null) {
            PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");

            setProperty("error", "true");
            setProperty("messageOnError", pluginManager.getMessage("userview.batchimportmenu.error.invalidForm", getClass().getName(), null));
        } else if (!(form.getLoadBinder() instanceof FormDataDeletableBinder)) {
            setProperty("disabledDelete", "true");
        }

        setProperty("view", "displayForm");
    }

    protected void displayJobStatus(Map model) {
        String jobId = getRequestParameterString("jobId");
        try {
            Map<String, Object> job = jobHelper.loadJob(jobId);
            if (job == null) {
                PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
                setProperty("error", "true");
                setProperty("messageOnError", pluginManager.getMessage("userview.batchimportmenu.error.jobNotFound", getClass().getName(), null));
                displayForm();
                return;
            }
            model.put("job", job);
            setProperty("importUrl", getUrl());
            setProperty("jobStatusUrl", getUrl() + "?action=status&jobId=" + jobId);
            setProperty("view", "jobStatus");
        } catch (SQLException e) {
            LogUtil.error(getClass().getName(), e, "");
            setProperty("error", "true");
            setProperty("messageOnError", e.getLocalizedMessage());
            displayForm();
        }
    }

    protected void handleSubmit(Map model) {
        String detectedFileType = "";
        String filename = "";
        try {
            Object[] columnMapping = (Object[]) getProperty("columnMapping");
            MultipartFile importFile = null;
            
            try {
                importFile = FileStore.getFile("csvImportFile");
            } catch (FileLimitException e) {
                setProperty("error", "true");
                setProperty("messageOnError", ResourceBundleUtil.getMessage("general.error.fileSizeTooLarge", new Object[]{FileStore.getFileSizeLimit()}));
                displayForm();
                return;
            }
            
            filename = importFile.getOriginalFilename();
            String normalizedFilename = filename.toUpperCase(Locale.ENGLISH);
            detectedFileType = getFileType(normalizedFilename);
            if (!normalizedFilename.endsWith(".CSV") && !normalizedFilename.endsWith(".XLS") && !normalizedFilename.endsWith(".XLSX")) {
                PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
                
                setProperty("error", "true");
                setProperty("messageOnError", pluginManager.getMessage("userview.batchimportmenu.error.invalidFileType", getClass().getName(), null));
                displayForm();
                return;
            }
            
                
            if ("true".equals(getPropertyString("checkUTF8")) && !isValidUTF8(importFile.getInputStream())) {
                PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
                
                setProperty("error", "true");
                setProperty("messageOnError", pluginManager.getMessage("userview.batchimportmenu.error.utf8", getClass().getName(), null));
                displayForm();
                return;
            }
            
            if (columnMapping != null && columnMapping.length > 0) {
                form = getForm();
                
                if (form != null) {
                    //validate mode
                    if (MODE_DELETE.equals(getRequestParameterString("mode")) && !(form.getLoadBinder() instanceof FormDataDeletableBinder)) {
                        PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
                    
                        setProperty("error", "true");
                        setProperty("messageOnError", pluginManager.getMessage("userview.batchimportmenu.error.deleteModeIsNotSupported", getClass().getName(), null));
                        displayForm();
                        return;
                    }
                    
                    if (getPropertyString("rowStart").isEmpty()) {
                        setProperty("rowStart", "0");
                    }

                    importFields = getMappedFields(columnMapping);
                    File tempFile = persistUpload(importFile, detectedFileType);
                    String jobId = jobHelper.createQueuedJob(
                            filename,
                            detectedFileType,
                            getRequestParameterString("mode"),
                            getFormTableName(form),
                            getBatchSize(),
                            getRowStart(),
                            getCurrentUsername(),
                            getCurrentUserFullName());
                    BatchImportJobConfig config = new BatchImportJobConfig(
                            jobId,
                            tempFile,
                            filename,
                            normalizedFilename,
                            detectedFileType,
                            columnMapping,
                            getFormTableName(form),
                            importFields,
                            getRequestParameterString("mode"),
                            getRowStart(),
                            getBatchSize(),
                            getPropertyString("key"),
                            getKey(),
                            getPropertyString("userviewKey"),
                            getPropertyString("customCsvDelimiter"),
                            getPropertyString("customCsvQuote"),
                            getCurrentUsername(),
                            getCurrentUserFullName());

                    Thread importThread = new PluginThread(new Runnable() {
                        public void run() {
                            runImportJob(config);
                        }
                    });
                    importThread.setDaemon(true);
                    importThread.start();

                    setProperty("jobId", jobId);
                    setProperty("jobStatusUrl", getUrl() + "?action=status&jobId=" + jobId);
                    setProperty("mode", config.mode);
                    if (getPropertyString("redirectUrl") != null && !getPropertyString("redirectUrl").trim().isEmpty()) {
                        setProperty("view", "queuedRedirect");
                    } else {
                        setProperty("view", "queued");
                    }
                    LogUtil.info(getClass().getName(), "Batch import queued. jobId=" + jobId + ", fileName=" + filename + ", fileType=" + detectedFileType + ", mode=" + config.mode + ", batchSize=" + config.batchSize);
                } else {
                    PluginManager pluginManager = (PluginManager)AppUtil.getApplicationContext().getBean("pluginManager");
                    
                    setProperty("error", "true");
                    setProperty("messageOnError", pluginManager.getMessage("userview.batchimportmenu.error.invalidForm", getClass().getName(), null));
                    displayForm();
                    return;
                }
            }
        } catch (IOException | SQLException | BeansException e) {
            LogUtil.error(getClass().getName(), e, "");
            setProperty("error", "true");
            setProperty("messageOnError", e.getLocalizedMessage());
            displayForm();
        }
    }
    
    protected FormService getFormService() {
        if (formService == null) {
            formService = (FormService) AppUtil.getApplicationContext().getBean("formService");
        }
        return formService;
    }
    
    protected boolean isValidUTF8(InputStream fis) {
        Boolean result = false;
        
        UniversalDetector detector = new UniversalDetector(null);
        byte[] buf = new byte[4096];
        try {
            int nread;
            while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
              detector.handleData(buf, 0, nread);
            }
            detector.dataEnd();

            String encoding = detector.getDetectedCharset();
            if (encoding != null && Constants.CHARSET_UTF_8.equals(encoding)) {
                result = true;
            }
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "");
        } finally {
            detector.reset();
            try {
                fis.close();
            } catch (Exception e) {}
        }
        
        return result;
    }
    
    protected Form getForm() {
        Form tempForm = null;
        FormDefinitionDao formDefinitionDao = (FormDefinitionDao) AppUtil.getApplicationContext().getBean("formDefinitionDao");
        String formDefId = getPropertyString("formDefId");
        if (formDefId != null) {
            AppDefinition appDef = AppUtil.getCurrentAppDefinition();
            FormDefinition formDef = formDefinitionDao.loadById(formDefId, appDef);
            if (formDef != null) {
                String formJson = formDef.getJson();

                if (formJson != null) {
                    tempForm = (Form) getFormService().createElementFromJson(formJson, true);
                }
            }
        }
        return tempForm;
    }

    protected File persistUpload(MultipartFile importFile, String fileType) throws IOException {
        String suffix = "." + (fileType == null || fileType.isEmpty() ? "tmp" : fileType.toLowerCase(Locale.ENGLISH));
        File tempFile = File.createTempFile("batch-import-", suffix);
        try (InputStream inputStream = importFile.getInputStream()) {
            Files.copy(inputStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    protected void runImportJob(BatchImportJobConfig config) {
        long startMillis = System.currentTimeMillis();
        BatchImportJobStats stats = new BatchImportJobStats();
        int errorCount = 0;
        String errorMessage = null;

        LogUtil.info(getClass().getName(), "Batch import started at " + formatTimestamp(startMillis)
                + ". jobId=" + config.jobId
                + ". fileName=" + config.originalFilename
                + ", fileType=" + config.fileType
                + ", mode=" + config.mode
                + ", batchSize=" + config.batchSize);

        try {
            jobHelper.markRunning(config.jobId, startMillis);
            batchImportDao.prepareTable(config.tableName, config.importFields);

            if (config.normalizedFilename.endsWith(".CSV")) {
                importCsv(config, stats);
            } else if (config.normalizedFilename.endsWith(".XLS")) {
                importXls(config, stats);
            } else if (config.normalizedFilename.endsWith(".XLSX")) {
                importXlsx(config, stats);
            }
        } catch (Exception e) {
            errorCount = 1;
            errorMessage = e.getLocalizedMessage();
            LogUtil.error(getClass().getName(), e, "Batch import failed. jobId=" + config.jobId + ", fileName=" + config.originalFilename + ", fileType=" + config.fileType);
        } finally {
            long endMillis = System.currentTimeMillis();
            long durationMs = endMillis - startMillis;
            String summary = buildJobSummary(config, stats, errorCount, durationMs);
            try {
                if (errorCount == 0) {
                    jobHelper.markCompleted(config.jobId, endMillis, durationMs, stats, summary);
                } else {
                    jobHelper.markFailed(config.jobId, endMillis, durationMs, stats, errorMessage, summary);
                }
            } catch (SQLException e) {
                LogUtil.error(getClass().getName(), e, "Unable to update batch import job status. jobId=" + config.jobId);
            }

            LogUtil.info(getClass().getName(), "Batch import ended at " + formatTimestamp(endMillis)
                    + ". jobId=" + config.jobId
                    + ", fileName=" + config.originalFilename
                    + ", fileType=" + config.fileType
                    + ", totalTimeMs=" + durationMs
                    + ", totalTime=" + formatDuration(durationMs)
                    + ", totalRows=" + stats.getTotalRows()
                    + ", processedRows=" + stats.getProcessedRows()
                    + ", imported=" + stats.getInsertedRows()
                    + ", updated=" + stats.getUpdatedRows()
                    + ", deleted=" + stats.getDeletedRows()
                    + ", skipped=" + stats.getSkippedRows()
                    + ", batches=" + stats.getBatchCount()
                    + ", errors=" + errorCount);

            if (config.file != null && config.file.exists() && !config.file.delete()) {
                LogUtil.warn(getClass().getName(), "Unable to delete batch import temp file: " + config.file.getAbsolutePath());
            }
        }
    }

    protected void importCsv(BatchImportJobConfig config, BatchImportJobStats stats) throws IOException, SQLException {
        try (InputStream fis = new FileInputStream(config.file)) {
            char quote = CSVReader.DEFAULT_QUOTE_CHARACTER;
            if (config.customCsvQuote != null && !config.customCsvQuote.isEmpty()) {
                quote = config.customCsvQuote.charAt(0);
            }

            char delimiter = resolveCsvDelimiter(config, quote);
            LogUtil.info(getClass().getName(), "Batch import CSV parser. fileName=" + config.originalFilename
                    + ", delimiter=" + describeDelimiter(delimiter)
                    + ", quote=" + describeDelimiter(quote));
            
            CSVReader reader = new CSVReader(new InputStreamReader(fis, "UTF-8"), delimiter, quote);
            String[] nextLine;

            int rowCount = 0;
            List<ImportRow> batch = new ArrayList<ImportRow>(config.batchSize);
            while ((nextLine = reader.readNext()) != null) {
                if (rowCount >= config.rowStart) {
                    batch.add(buildCsvRow(config, rowCount, nextLine));
                    if (batch.size() >= config.batchSize) {
                        flushBatch(config, batch, stats);
                    }
                }
                rowCount++;
            }
            flushBatch(config, batch, stats);
        }
    }

    protected void importXls(BatchImportJobConfig config, BatchImportJobStats stats) throws IOException, SQLException {
        try (InputStream fis = new FileInputStream(config.file)) {
            HSSFWorkbook workbook = new HSSFWorkbook(fis);
            HSSFSheet sheet = workbook.getSheetAt(0);
            iterateExcelData(config, sheet.iterator(), stats);
        }
    }

    protected void importXlsx(BatchImportJobConfig config, BatchImportJobStats stats) throws Exception {
        LogUtil.info(getClass().getName(), "Batch import XLSX parser using streaming reader. fileName=" + config.originalFilename);
        try (OPCPackage pkg = OPCPackage.open(config.file, PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg, true);
            reader.setUseReadOnlySharedStringsTable(true);
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();

            Iterator<InputStream> sheets = reader.getSheetsData();
            if (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    XMLReader parser = SAXHelper.newXMLReader();
                    parser.setContentHandler(new XlsxSheetHandler(config, stats, sharedStrings, styles));
                    parser.parse(new InputSource(sheet));
                }
            }
        }
    }

    protected void iterateExcelData(BatchImportJobConfig config, Iterator<Row> rowIterator, BatchImportJobStats stats) throws SQLException {
        int rowCount = 0;
        List<ImportRow> batch = new ArrayList<ImportRow>(config.batchSize);
        while(rowIterator.hasNext()) {
            Row row = rowIterator.next();
            if (rowCount >= config.rowStart) {
                batch.add(buildExcelRow(config, rowCount, row));
                if (batch.size() >= config.batchSize) {
                    flushBatch(config, batch, stats);
                }
            }
            rowCount++;
        }
        flushBatch(config, batch, stats);
    }

    protected ImportRow buildCsvRow(BatchImportJobConfig config, int rowNumber, String[] row) {
        ImportRow importRow = new ImportRow(rowNumber);
        setPrimaryKey(importRow, getCsvValue(row, config.keyColumn));

        for (Object o : config.columnMapping) {
            Map column = (HashMap) o;
            String field = column.get("field").toString();
            String value = getCsvValue(row, column.get("col").toString());
            if (value != null && !value.isEmpty()) {
                importRow.put(field, value);
            }
        }

        addUserviewKey(importRow, config);
        return importRow;
    }

    protected ImportRow buildExcelRow(BatchImportJobConfig config, int rowNumber, Row row) {
        ImportRow importRow = new ImportRow(rowNumber);
        setPrimaryKey(importRow, getExcelColumnValue(row, config.keyColumn, false));

        for (Object o : config.columnMapping) {
            Map column = (HashMap) o;
            String field = column.get("field").toString();
            boolean formatted = column.get("formatted") != null && "true".equalsIgnoreCase(column.get("formatted").toString());
            String value = getExcelColumnValue(row, column.get("col").toString(), formatted);
            if (value != null && !value.isEmpty()) {
                importRow.put(field, value);
            }
        }

        addUserviewKey(importRow, config);
        return importRow;
    }

    protected void flushBatch(BatchImportJobConfig config, List<ImportRow> batch, BatchImportJobStats stats) throws SQLException {
        if (batch.isEmpty()) {
            return;
        }

        int batchRows = batch.size();
        BatchImportResult result = new BatchImportResult();
        if (getFieldsInBatch(batch).isEmpty() && !MODE_DELETE.equals(config.mode)) {
            result.addSkipped(batchRows);
            stats.addBatch(batchRows, result);
            jobHelper.updateProgress(config.jobId, stats);
            batch.clear();
            return;
        }

        result = batchImportDao.saveBatch(config.tableName, config.importFields, batch, config.mode, config.username, config.userFullName);
        stats.addBatch(batchRows, result);
        jobHelper.updateProgress(config.jobId, stats);
        batch.clear();
    }

    protected void addUserviewKey(ImportRow row, BatchImportJobConfig config) {
        if (config.userviewKeyValue != null && config.userviewKeyValue.trim().length() > 0
                && config.userviewKeyField != null && config.userviewKeyField.trim().length() > 0) {
            row.put(config.userviewKeyField, config.userviewKeyValue);
        }
    }
    protected void setPrimaryKey(ImportRow row, String value) {
        if (value != null && !value.trim().isEmpty()) {
            row.setId(value);
        }
    }

    protected String getCsvValue(String[] row, String columnNumber) {
        if (columnNumber != null && columnNumber.trim().length() > 0) {
            int colNum = Integer.parseInt(columnNumber);
            if (colNum < row.length) {
                return row[colNum];
            }
        }
        return null;
    }

    protected String getExcelColumnValue(Row row, String columnNumber, boolean formatted) {
        if (columnNumber != null && columnNumber.trim().length() > 0) {
            int colNum = Integer.parseInt(columnNumber);
            if (row.getCell(colNum) != null) {
                return getExcelCellValue(row.getCell(colNum), formatted);
            }
        }
        return null;
    }

    protected ImportRow buildXlsxRow(BatchImportJobConfig config, int rowNumber, Map<Integer, XlsxCellValue> row, SharedStrings sharedStrings, StylesTable styles) {
        ImportRow importRow = new ImportRow(rowNumber);
        setPrimaryKey(importRow, getXlsxColumnValue(row, config.keyColumn, false, sharedStrings, styles));

        for (Object o : config.columnMapping) {
            Map column = (HashMap) o;
            String field = column.get("field").toString();
            boolean formatted = column.get("formatted") != null && "true".equalsIgnoreCase(column.get("formatted").toString());
            String value = getXlsxColumnValue(row, column.get("col").toString(), formatted, sharedStrings, styles);
            if (value != null && !value.isEmpty()) {
                importRow.put(field, value);
            }
        }

        addUserviewKey(importRow, config);
        return importRow;
    }

    protected String getXlsxColumnValue(Map<Integer, XlsxCellValue> row, String columnNumber, boolean formatted, SharedStrings sharedStrings, StylesTable styles) {
        if (columnNumber != null && columnNumber.trim().length() > 0) {
            int colNum = Integer.parseInt(columnNumber);
            XlsxCellValue value = row.get(colNum);
            if (value != null) {
                return getXlsxCellValue(value, formatted, sharedStrings, styles);
            }
        }
        return null;
    }

    protected String getXlsxCellValue(XlsxCellValue cell, boolean formatted, SharedStrings sharedStrings, StylesTable styles) {
        String value = cell.value;
        if (value == null) {
            return null;
        }

        if ("s".equals(cell.type)) {
            int index = Integer.parseInt(value);
            return sharedStrings.getItemAt(index).getString();
        } else if ("inlineStr".equals(cell.type) || "str".equals(cell.type)) {
            return value;
        } else if ("b".equals(cell.type)) {
            return "1".equals(value) ? "TRUE" : "FALSE";
        }

        if (formatted && cell.styleIndex >= 0) {
            try {
                XSSFCellStyle style = styles.getStyleAt(cell.styleIndex);
                DataFormatter formatter = new DataFormatter();
                return formatter.formatRawCellContents(Double.parseDouble(value), style.getDataFormat(), style.getDataFormatString());
            } catch (Exception e) {
                return value;
            }
        }
        return value;
    }

    protected int getColumnIndexFromCellReference(String cellReference) {
        if (cellReference == null || cellReference.isEmpty()) {
            return -1;
        }

        int column = 0;
        for (int i = 0; i < cellReference.length(); i++) {
            char c = cellReference.charAt(i);
            if (Character.isDigit(c)) {
                break;
            }
            column = (column * 26) + (Character.toUpperCase(c) - 'A' + 1);
        }
        return column - 1;
    }

    protected int getRowIndexFromCellReference(String cellReference, int fallback) {
        if (cellReference == null || cellReference.isEmpty()) {
            return fallback;
        }

        StringBuilder row = new StringBuilder();
        for (int i = 0; i < cellReference.length(); i++) {
            char c = cellReference.charAt(i);
            if (Character.isDigit(c)) {
                row.append(c);
            }
        }
        if (row.length() == 0) {
            return fallback;
        }
        return Integer.parseInt(row.toString()) - 1;
    }

    protected Set<String> getMappedFields(Object[] columnMapping) {
        Set<String> fields = new LinkedHashSet<String>();
        for (Object o : columnMapping) {
            Map column = (HashMap) o;
            if (column.get("field") != null && !column.get("field").toString().trim().isEmpty()) {
                fields.add(column.get("field").toString());
            }
        }
        if (getKey() != null && getKey().trim().length() > 0 && getPropertyString("userviewKey") != null && getPropertyString("userviewKey").trim().length() > 0) {
            fields.add(getPropertyString("userviewKey"));
        }
        return fields;
    }

    protected Set<String> getFieldsInBatch(List<ImportRow> batch) {
        Set<String> fields = new LinkedHashSet<String>();
        for (ImportRow row : batch) {
            fields.addAll(row.getValues().keySet());
        }
        return fields;
    }

    protected String getFormTableName(Form form) {
        String tableName = form.getPropertyString("tableName");
        if (tableName == null || tableName.trim().length() == 0) {
            tableName = form.getPropertyString(FormUtil.PROPERTY_ID);
        }
        return FormDataDaoImpl.FORM_PREFIX_TABLE_NAME + tableName;
    }

    protected String getCurrentUsername() {
        String username = WorkflowUtil.getCurrentUsername();
        return username == null ? "" : username;
    }

    protected String getCurrentUserFullName() {
        String name = WorkflowUtil.getCurrentUserFullName();
        return name == null ? "" : name;
    }

    protected int getBatchSize() {
        int batchSize = DEFAULT_BATCH_SIZE;
        String configuredBatchSize = getPropertyString("batchSize");
        if (configuredBatchSize != null && !configuredBatchSize.trim().isEmpty()) {
            try {
                batchSize = Integer.parseInt(configuredBatchSize.trim());
            } catch (NumberFormatException e) {
                batchSize = DEFAULT_BATCH_SIZE;
            }
        }
        if (batchSize <= 0) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    protected int getRowStart() {
        try {
            return Integer.parseInt(getPropertyString("rowStart"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected char resolveCsvDelimiter(BatchImportJobConfig config, char quote) throws IOException {
        char configuredDelimiter = CSVReader.DEFAULT_SEPARATOR;
        boolean hasConfiguredDelimiter = config.customCsvDelimiter != null && !config.customCsvDelimiter.trim().isEmpty();
        if (hasConfiguredDelimiter) {
            configuredDelimiter = config.customCsvDelimiter.trim().charAt(0);
        }

        char detectedDelimiter = detectCsvDelimiter(config.file, quote);
        if (!hasConfiguredDelimiter) {
            return detectedDelimiter;
        }

        int configuredScore = scoreCsvDelimiter(config.file, configuredDelimiter, quote);
        int detectedScore = scoreCsvDelimiter(config.file, detectedDelimiter, quote);
        if (detectedDelimiter != configuredDelimiter && configuredScore == 0 && detectedScore > 0) {
            LogUtil.warn(getClass().getName(), "Configured CSV delimiter " + describeDelimiter(configuredDelimiter)
                    + " did not match sampled file content. Using detected delimiter " + describeDelimiter(detectedDelimiter)
                    + " for fileName=" + config.originalFilename);
            return detectedDelimiter;
        }
        return configuredDelimiter;
    }

    protected char detectCsvDelimiter(File file, char quote) throws IOException {
        char[] candidates = new char[]{',', ';', '\t', '|'};
        char bestDelimiter = CSVReader.DEFAULT_SEPARATOR;
        int bestScore = -1;

        for (char candidate : candidates) {
            int score = scoreCsvDelimiter(file, candidate, quote);
            if (score > bestScore) {
                bestScore = score;
                bestDelimiter = candidate;
            }
        }
        return bestDelimiter;
    }

    protected int scoreCsvDelimiter(File file, char delimiter, char quote) throws IOException {
        int score = 0;
        int sampled = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null && sampled < CSV_DELIMITER_SAMPLE_LINES) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                score += countDelimiterOutsideQuotes(line, delimiter, quote);
                sampled++;
            }
        }
        return score;
    }

    protected int countDelimiterOutsideQuotes(String line, char delimiter, char quote) {
        int count = 0;
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == quote) {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == quote) {
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == delimiter && !quoted) {
                count++;
            }
        }
        return count;
    }

    protected String describeDelimiter(char delimiter) {
        if (delimiter == '\t') {
            return "TAB";
        } else if (delimiter == CSVReader.DEFAULT_SEPARATOR) {
            return ",";
        }
        return String.valueOf(delimiter);
    }

    protected String getFileType(String normalizedFilename) {
        if (normalizedFilename == null) {
            return "";
        } else if (normalizedFilename.endsWith(".CSV")) {
            return "CSV";
        } else if (normalizedFilename.endsWith(".XLSX")) {
            return "XLSX";
        } else if (normalizedFilename.endsWith(".XLS")) {
            return "XLS";
        }
        return "UNKNOWN";
    }

    protected String formatTimestamp(long millis) {
        return new SimpleDateFormat(LOG_DATE_FORMAT).format(new Date(millis));
    }

    protected String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes % 60, seconds % 60, millis % 1000);
    }

    protected String buildJobSummary(BatchImportJobConfig config, BatchImportJobStats stats, int errorCount, long durationMs) {
        return "jobId=" + config.jobId
                + ", fileName=" + config.originalFilename
                + ", fileType=" + config.fileType
                + ", mode=" + config.mode
                + ", batchSize=" + config.batchSize
                + ", durationMs=" + durationMs
                + ", totalTime=" + formatDuration(durationMs)
                + ", totalRows=" + stats.getTotalRows()
                + ", processedRows=" + stats.getProcessedRows()
                + ", inserted=" + stats.getInsertedRows()
                + ", updated=" + stats.getUpdatedRows()
                + ", deleted=" + stats.getDeletedRows()
                + ", skipped=" + stats.getSkippedRows()
                + ", batches=" + stats.getBatchCount()
                + ", errors=" + errorCount;
    }

    protected String getExcelCellValue(Cell cell, boolean formatted) {
        if (cell.getCellType() == CellType.STRING) {
            return cell.getRichStringCellValue().getString();
        } else {
            if (formatted && cell.getCellType() != CellType.FORMULA) {
                DataFormatter formatter = new DataFormatter();
                String val = formatter.formatCellValue(cell);
                return val;
            } else {
                String val = null;
                if (cell instanceof HSSFCell) {
                    if (cell.getCellType() == CellType.NUMERIC) {
                        val = String.valueOf(((HSSFCell) cell).getNumericCellValue());
                    } else {
                        val = ((HSSFCell) cell).getStringCellValue();
                    }
                } else {
                    val = ((XSSFCell) cell).getRawValue();
                    if (val == null) {
                        val = ((XSSFCell) cell).getStringCellValue();
                    }
                }
                return val;
            }
        }
    }

    protected static class BatchImportJobConfig {

        final String jobId;
        final File file;
        final String originalFilename;
        final String normalizedFilename;
        final String fileType;
        final Object[] columnMapping;
        final String tableName;
        final Set<String> importFields;
        final String mode;
        final int rowStart;
        final int batchSize;
        final String keyColumn;
        final String userviewKeyValue;
        final String userviewKeyField;
        final String customCsvDelimiter;
        final String customCsvQuote;
        final String username;
        final String userFullName;

        BatchImportJobConfig(String jobId, File file, String originalFilename, String normalizedFilename, String fileType, Object[] columnMapping,
                String tableName, Set<String> importFields, String mode, int rowStart, int batchSize, String keyColumn,
                String userviewKeyValue, String userviewKeyField, String customCsvDelimiter, String customCsvQuote,
                String username, String userFullName) {
            this.jobId = jobId;
            this.file = file;
            this.originalFilename = originalFilename;
            this.normalizedFilename = normalizedFilename;
            this.fileType = fileType;
            this.columnMapping = columnMapping;
            this.tableName = tableName;
            this.importFields = importFields;
            this.mode = mode;
            this.rowStart = rowStart;
            this.batchSize = batchSize;
            this.keyColumn = keyColumn;
            this.userviewKeyValue = userviewKeyValue;
            this.userviewKeyField = userviewKeyField;
            this.customCsvDelimiter = customCsvDelimiter;
            this.customCsvQuote = customCsvQuote;
            this.username = username;
            this.userFullName = userFullName;
        }
    }

    protected class XlsxSheetHandler extends DefaultHandler {

        private final BatchImportJobConfig config;
        private final BatchImportJobStats stats;
        private final SharedStrings sharedStrings;
        private final StylesTable styles;
        private final List<ImportRow> batch;
        private final Map<Integer, XlsxCellValue> rowValues = new HashMap<Integer, XlsxCellValue>();
        private final StringBuilder value = new StringBuilder();
        private String cellReference;
        private String cellType;
        private int cellStyleIndex = -1;
        private int currentRowNumber = -1;
        private int nextRowNumber = 0;
        private boolean collectingValue = false;

        XlsxSheetHandler(BatchImportJobConfig config, BatchImportJobStats stats, SharedStrings sharedStrings, StylesTable styles) {
            this.config = config;
            this.stats = stats;
            this.sharedStrings = sharedStrings;
            this.styles = styles;
            this.batch = new ArrayList<ImportRow>(config.batchSize);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            String name = getElementName(localName, qName);
            if ("row".equals(name)) {
                rowValues.clear();
                String rowNumber = attributes.getValue("r");
                if (rowNumber != null && !rowNumber.isEmpty()) {
                    currentRowNumber = Integer.parseInt(rowNumber) - 1;
                    nextRowNumber = currentRowNumber + 1;
                } else {
                    currentRowNumber = nextRowNumber++;
                }
            } else if ("c".equals(name)) {
                cellReference = attributes.getValue("r");
                cellType = attributes.getValue("t");
                cellStyleIndex = parseCellStyleIndex(attributes.getValue("s"));
                value.setLength(0);
            } else if ("v".equals(name) || "t".equals(name)) {
                value.setLength(0);
                collectingValue = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (collectingValue) {
                value.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String name = getElementName(localName, qName);
            if ("v".equals(name) || "t".equals(name)) {
                collectingValue = false;
            } else if ("c".equals(name)) {
                int columnIndex = getColumnIndexFromCellReference(cellReference);
                if (columnIndex >= 0) {
                    rowValues.put(columnIndex, new XlsxCellValue(value.toString(), cellType, cellStyleIndex));
                }
            } else if ("row".equals(name)) {
                if (currentRowNumber >= config.rowStart) {
                    batch.add(buildXlsxRow(config, currentRowNumber, rowValues, sharedStrings, styles));
                    if (batch.size() >= config.batchSize) {
                        flushCurrentBatch();
                    }
                }
            }
        }

        @Override
        public void endDocument() throws SAXException {
            flushCurrentBatch();
        }

        private void flushCurrentBatch() throws SAXException {
            try {
                flushBatch(config, batch, stats);
            } catch (SQLException e) {
                throw new SAXException(e);
            }
        }

        private String getElementName(String localName, String qName) {
            return localName == null || localName.isEmpty() ? qName : localName;
        }

        private int parseCellStyleIndex(String value) {
            if (value == null || value.isEmpty()) {
                return -1;
            }
            return Integer.parseInt(value);
        }
    }

    protected static class XlsxCellValue {

        final String value;
        final String type;
        final int styleIndex;

        XlsxCellValue(String value, String type, int styleIndex) {
            this.value = value;
            this.type = type;
            this.styleIndex = styleIndex;
        }
    }
    
    @Override
    public String getOfflineOptions() {
        return null;
    }
}
