package org.joget.marketplace.dataimport.batchimport;

import java.util.ArrayList;
import java.util.Collection;
import org.joget.commons.util.LogUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    /**
     * Classes whose static initialisation reads JVM state. For example, the log4j StatusLogger
     * (used by Apache POI) reads all system properties when it is first loaded. On servers that
     * run a security manager (e.g. multi-tenant cloud) that call is blocked while an import job
     * runs, which made every Excel import fail. They are initialised once here, when the plugin
     * starts, instead.
     */
    private static final String[] PREWARM_CLASSES = {
        "org.apache.logging.log4j.status.StatusLogger",
        "org.apache.logging.log4j.LogManager",
        "org.apache.poi.openxml4j.opc.OPCPackage",
        "org.apache.poi.openxml4j.opc.PackageAccess",
        "org.apache.poi.xssf.eventusermodel.XSSFReader",
        "org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable",
        "org.apache.poi.xssf.model.StylesTable",
        "org.apache.poi.ooxml.util.SAXHelper",
        "org.apache.poi.ss.usermodel.DataFormatter",
        "org.apache.poi.ss.usermodel.CellType",
        "org.apache.poi.hssf.usermodel.HSSFWorkbook",
        "org.apache.poi.xssf.usermodel.XSSFCell"
    };

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        prewarm();

        registrationList = new ArrayList<ServiceRegistration>();

        registrationList.add(context.registerService(BatchImportMenu.class.getName(), new BatchImportMenu(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }

    /**
     * Loads and initialises the Excel and logging classes once. Never throws: the plugin must
     * still start even if this fails.
     */
    private void prewarm() {
        StringBuilder failures = new StringBuilder();
        ClassLoader loader = Activator.class.getClassLoader();
        for (String className : PREWARM_CLASSES) {
            try {
                Class.forName(className, true, loader);
            } catch (Throwable t) {
                failures.append(className.substring(className.lastIndexOf('.') + 1))
                        .append(" (").append(t.getClass().getSimpleName()).append(") ");
            }
        }
        if (failures.length() > 0) {
            try {
                LogUtil.warn(Activator.class.getName(), "Batch import: unable to pre-load " + failures.toString().trim());
            } catch (Throwable ignore) {
                // logging must never stop the plugin from starting
            }
        }
    }
}
