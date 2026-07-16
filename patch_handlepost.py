def patch_file(file_name):
    with open(file_name, "r") as f:
        content = f.read()

    old_code = """        if (result.validTransactions.length > 0) {
          const summaryService = new SummaryService();
          summaryService.updateDashboard(result.validTransactions);
        }
        
        // Simulasikan pemrosesan dari queue
        currentQueue = parseInt(props.getProperty("UPLOAD_QUEUE_COUNT") || "0");
        let remainingQueue = Math.max(0, currentQueue - transactionsReceived);
        props.setProperty("UPLOAD_QUEUE_COUNT", remainingQueue.toString());

        const duration = new Date().getTime() - startTime;
        Logger.logAction("syncTransactions", `Success: ${result.insertedCount} inserted, ${result.skippedCount} skipped. AppVer: ${data.appVersion || "Unknown"}`);
        Logger.logPerformance("syncTransactions", duration, `Inserted: ${result.insertedCount}`);
        
        return ResponseUtil.success({"""
        
    new_code = """        if (result.validTransactions.length > 0) {
          const summaryService = new SummaryService();
          summaryService.updateDashboard(result.validTransactions);
        }
        
        // Simulasikan pemrosesan dari queue
        currentQueue = parseInt(props.getProperty("UPLOAD_QUEUE_COUNT") || "0");
        let remainingQueue = Math.max(0, currentQueue - transactionsReceived);
        props.setProperty("UPLOAD_QUEUE_COUNT", remainingQueue.toString());

        const duration = new Date().getTime() - startTime;
        Logger.logAction("syncTransactions", `Success: ${result.insertedCount} inserted, ${result.skippedCount} skipped. AppVer: ${data.appVersion || "Unknown"}`);
        Logger.logPerformance("syncTransactions", duration, `Inserted: ${result.insertedCount}`);
        
        invalidateAllCaches();
        
        return ResponseUtil.success({"""

    if old_code in content:
        content = content.replace(old_code, new_code)
        with open(file_name, "w") as f:
            f.write(content)
        print(f"Patched {file_name}")
    else:
        print(f"Failed to find target in {file_name}")

patch_file("AppsScript_Full.gs")
patch_file("AppsScript.gs")
