<#if element.properties.isPreview! == 'true' >
    <script>
        $(document).ready(function() {
            $(".form-button").attr("disabled", "disabled");
        });
    </script>
</#if>

<div class="viewCsvImport-body-content">
    <#if element.properties.customHeader! == '' >
        <h3>${element.properties.label!}</h3>
    <#else>
        ${element.properties.customHeader!}
    </#if>
    <#if element.properties.view! == 'queued'>
        <div class="result">
            <span>@@userview.batchimportmenu.importQueued@@</span>
            <#if element.properties.jobId! != ''>
                <span> @@userview.batchimportmenu.jobId@@: ${element.properties.jobId!}</span>
            </#if>
            <#if element.properties.jobStatusUrl! != ''>
                <br/>
                <a href="${element.properties.jobStatusUrl!}">@@userview.batchimportmenu.viewJobStatus@@</a>
            </#if>
        </div>
    <#elseif element.properties.view! == 'queuedRedirect'>
        <div class="batch-import-redirect-notice">
            <style>
                .batch-import-redirect-notice{display:flex; align-items:center; gap:22px; padding:32px 36px; border:1px solid #d9e6db; background:#f3faf4; border-radius:10px; max-width:600px; margin:20px auto; box-shadow:0 2px 10px rgba(0,0,0,0.06);}
                .batch-import-redirect-notice .spinner{width:44px; height:44px; border:4px solid #cfe8d3; border-top-color:#28a745; border-radius:50%; animation:batch-import-spin 0.8s linear infinite; flex-shrink:0;}
                .batch-import-redirect-notice .text-block{line-height:1.5;}
                .batch-import-redirect-notice .title{font-size:18px; font-weight:600; color:#1c4522; margin-bottom:4px;}
                .batch-import-redirect-notice .message{font-size:15px; color:#256029;}
                .batch-import-redirect-notice .caption{font-size:13px; color:#5c8a63; margin-top:6px;}
                @keyframes batch-import-spin{to{transform:rotate(360deg);}}
            </style>
            <div class="spinner"></div>
            <div class="text-block">
                <div class="title">@@userview.batchimportmenu.importStartedTitle@@</div>
                <div class="message">
                    <#if element.properties.messageOnSuccess! != '' >
                        ${element.properties.messageOnSuccess!}
                    <#else>
                        @@userview.batchimportmenu.importStartedRedirecting@@
                    </#if>
                </div>
                <div class="caption">@@userview.batchimportmenu.redirectingCaption@@</div>
            </div>
        </div>
        <#if element.properties.redirectUrl! != '' >
            <script>
                setTimeout(function(){
                    parent.location = "${element.properties.redirectUrl!}";
                }, 3000);
            </script>
        </#if>
    <#elseif element.properties.view! == 'jobStatus'>
        <#if job??>
            <div class="result batch-import-job-status">
                <style>
                    .batch-import-job-status table{width:100%; max-width:900px;}
                    .batch-import-job-status th{text-align:left; width:260px;}
                    .batch-import-job-status th,.batch-import-job-status td{padding:6px 8px; border-bottom:1px solid #eee;}
                </style>
                <h4>@@userview.batchimportmenu.jobStatus@@</h4>
                <p>
                    <a href="${element.properties.importUrl!}">@@userview.batchimportmenu.backToImport@@</a>
                    &nbsp;|&nbsp;
                    <a href="${element.properties.jobStatusUrl!}">@@userview.batchimportmenu.refresh@@</a>
                </p>
                <table class="table table-bordered">
                    <tbody>
                        <tr><th>@@userview.batchimportmenu.jobId@@</th><td>${job.id!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.status@@</th><td>${job.status!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.fileName@@</th><td>${job.fileName!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.fileType@@</th><td>${job.fileType!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.mode@@</th><td>${job.mode!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.batchSize@@</th><td>${job.batchSize!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.totalRows@@</th><td>${job.totalRows!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.processedRows@@</th><td>${job.processedRows!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.insertedRows@@</th><td>${job.insertedRows!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.updatedRows@@</th><td>${job.updatedRows!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.deletedRows@@</th><td>${job.deletedRows!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.skippedRows@@</th><td>${job.skippedRows!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.errorRows@@</th><td>${job.errorRows!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.batchCount@@</th><td>${job.batchCount!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.durationMs@@</th><td>${job.durationMs!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.createdAt@@</th><td>${job.createdAt!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.startedAt@@</th><td>${job.startedAt!}</td></tr>
                        <tr><th>@@userview.batchimportmenu.completedAt@@</th><td>${job.completedAt!}</td></tr>
                        <#if job.errorMessage?? && job.errorMessage != ''>
                            <tr><th>@@userview.batchimportmenu.errorMessage@@</th><td>${job.errorMessage!}</td></tr>
                        </#if>
                    </tbody>
                </table>
            </div>
        </#if>
    <#elseif element.properties.view! == 'success'>
        <#if element.properties.messageOnSuccess! != '' >
            <script>
                alert("${element.properties.messageOnSuccess!}");
            </script>
        </#if>
        <#if element.properties.redirectUrl! != '' >
            <script>
                parent.location = "${element.properties.redirectUrl!}";
            </script>
        </#if>
        <div class="result">
            <style>
                .toggle{display:block; margin-top:5px;cursor:pointer;}
            </style>
            <#if element.properties.mode! == 'DELETE' >
                <a class="toggle">${successDeletedCount!} @@userview.batchimportmenu.recordsDeleted@@</a>
                <div style="display:none">
                    @@userview.batchimportmenu.rowNum@@<br/>
                    ${successDeletedRows!}
                </div>
            <#else>
                <a class="toggle">${successImportedCount!} @@userview.batchimportmenu.recordsImported@@</a>
                <div style="display:none">
                    @@userview.batchimportmenu.rowNum@@<br/>
                    ${successImportedRows!}
                </div>
                <#if successUpdatedCount! != 0 >
                    <a class="toggle">${successUpdatedCount!} @@userview.batchimportmenu.recordsUpdated@@</a>
                    <div style="display:none">
                        @@userview.batchimportmenu.rowNum@@<br/>
                        ${successUpdatedRows!}
                    </div>
                </#if>
            </#if>
            <#if skippedCount! != 0 >
                <a class="toggle">${skippedCount!} @@userview.batchimportmenu.recordsSkipped@@</a>
                <div style="display:none">
                    @@userview.batchimportmenu.rowNum@@<br/>
                    ${skippedRows!}
                </div>
            </#if>
            <#if validationErrorCount! != 0 >
                <a class="toggle">${validationErrorCount!} @@userview.batchimportmenu.recordsError@@</a>
                <div style="display:none">
                    <table cellspacing="0" style="width:100%;" class="table table-bordered">
                        <tbody>
                        <tr>
                            <th width="20%">@@userview.batchimportmenu.row@@</th>
                            <th>@@userview.batchimportmenu.errors@@</th>
                        </tr>
                        <#list validationErrorRows?keys as row>
                            <tr class="grid-row">
                                <td>${row}</td>
                                <td>
                                    ${validationErrorRows[row]}
                                </td>
                            </tr>
                        </#list>
                        </tbody>
                    </table>
                </div>    
            </#if>
        </div>
    <#elseif element.properties.view! == 'displayForm'>
        <fieldset id="form-canvas">
            <#if element.properties.error! == 'true' >
                <style>
                    .errors{color:red; margin-bottom:20px;}
                </style>
                <div class="errors">
                    <span>
                        <#if element.properties.messageOnError! != '' >
                            ${element.properties.messageOnError!}
                        <#else>
                            @@userview.batchimportmenu.error@@
                        </#if>
                    </span>
                </div>
            </#if>
            <form id="csvImportForm" method="post" action="${element.properties.url!}" class="form form-container" enctype="multipart/form-data">
                <div class="form-section no_label" id="section_upload">
                    <div class="form-section-title"></div>
                    <div style="width: 100%" class="form-column" id="">
                        <div class="form-cell">
                            <label for="csvImportFile" class="label upload">@@general.method.label.selectFile@@ <span class="form-cell-validator">*</span></label>
                            <input id="csvImportFile" type="file" name="csvImportFile"/>
                        </div>
                        <div class="form-cell">
                            <label for="mode" class="label upload">@@userview.batchimportmenu.importMode@@</label>
                            <div class="form-cell-value">
                                <label style="display:block; width:100%; float:none;">
                                    <input class="form-check-input" type="radio" value="NEW" name="mode"><i></i>
                                    @@userview.batchimportmenu.importMode.new@@
                                </label>
                                <label style="display:block; width:100%; float:none;">
                                    <input class="form-check-input" type="radio" value="NEW & UPDATE" name="mode" checked><i></i>
                                    @@userview.batchimportmenu.importMode.new_update@@
                                </label>
                                <#if element.properties.disabledDelete! != 'true' >
                                    <label style="display:block; width:100%; float:none;">
                                        <input class="form-check-input" type="radio" value="DELETE" name="mode"><i></i>
                                        @@userview.batchimportmenu.importMode.delete@@
                                    </label>
                                </#if>
                            </div>
                        </div>
                    </div>
                    <div style="clear:both"></div>
                </div>
                <div class="form-section no_label section_" id="section-actions">
                    <div class="form-section-title"></div>
                    <div style="width: " class="form-column form-column-horizontal" id="">
                        <div class="form-cell">
                            <input type="submit" value="@@general.method.label.upload@@" class="form-button btn button" name="submit" id="submit">
                        </div>
                    </div>
                </div>    
            </form>
        </fieldset>
    </#if>
    <#if element.properties.customFooter! != '' >
        ${element.properties.customFooter!}
    </#if>
</div>
<script>
    $(document).ready(function(){
        <#if element.properties.corfirmation! != '' >
            $("form#csvImportForm").submit(function(){
                return confirm("${element.properties.corfirmation!}");
            });
        </#if>

        $(".toggle").click(function(){
            $(this).next().toggle();
        });
    });
</script>
