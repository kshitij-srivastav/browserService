public class RestController {

    private final BrowserService browserService;

    @Autowired
    public RestController(BrowserService browserService) {
        this.browserService = browserService;
    }

    /**
     * Opens Safari on macOS and navigates to the specified URL.
     *
     * @param url the website URL to open
     * @return a response indicating success or failure
     */
    @GetMapping("/open-safari")
    public String openSafari(@RequestParam String url) {
        if (Objects.isNull(url) || url.isBlank()) {
            log.error("URL cannot be null or empty");
            return "Failed: URL cannot be null or empty";
        }

        log.info("Opening Safari with URL: {}", url);

        try {
            // macOS-specific command to open Safari
            ProcessBuilder processBuilder = new ProcessBuilder("open", "-a", "Safari", url);
            processBuilder.start();
            log.info("Safari opened successfully with URL: {}", url);
            return "Success: Safari opened with URL " + url;
        } catch (IOException e) {
            log.error("Error opening Safari with URL: {}", url, e);
            return "Failed: Error opening Safari";
        }
    }

    @GetMapping("/open-browser")
    public String openBrowser(@RequestParam String url, @RequestParam String browserName) {
        if (Objects.isNull(url) || url.isBlank() || Objects.isNull(browserName) || browserName.isBlank()) {
            log.error("URL and browser name cannot be null or empty");
            return "Failed: URL and browser name cannot be null or empty";
        }

        log.info("Opening browser with URL: {} and browser name: {}", url, browserName);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("open", "-a", browserName, url);
            processBuilder.start();
            log.info("Browser opened successfully with URL: {} and browser name: {}", url, browserName);
            return "Success: Browser opened with URL " + url + " and browser name " + browserName;
        } catch (IOException e) {
            log.error("Error opening browser with URL: {} and browser name: {}", url, browserName, e);
            return "Failed: Error opening browser";
        }
    }

    @GetMapping("/close-browser")
    public void closeBrowser(@RequestParam String browserName) {
        if (Objects.isNull(browserName) || browserName.isBlank()) {
            log.error("Browser name cannot be null or empty");
            return;
        }

        log.info("Closing browser with browser name: {}", browserName);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("killall", browserName);
            processBuilder.start();
            log.info("Browser closed successfully with browser name: {}", browserName);
        } catch (IOException e) {
            log.error("Error closing browser with browser name: {}", browserName, e);
        }
    }

    /**
     * Fetches the URL of the current active tab in Safari.
     *
     * @return The URL of the current tab or an error message if it cannot be fetched.
     */
    @GetMapping("/current-tab-url/safari")
    public String getCurrentTabUrlSafari() {
        String script = "osascript -e 'tell application \"Safari\" to get URL of front document'";
        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", script);

        try {
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String url = reader.readLine();
            process.waitFor();
            return url != null ? url : "No active tab found";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error fetching URL: " + e.getMessage();
        }
    }

    @DeleteMapping("/last-session")
    public String deleteLastSession(@RequestHeader("browser-name") String browserName) {
        if (browserName.equalsIgnoreCase("Safari")) {
             deleteSafariHistory();
        } else if (browserName.equalsIgnoreCase("Chrome")) {
            return deleteChromeSession();
        } else {
            return "Browser not supported.";
        }
        return "Last session deleted successfully.";
    }


    /**
     * Deletes Safari's last session.
     *
     * @return A message indicating success or failure.
     */
    private String deleteSafariSession() {


        String script = """
                osascript -e 'tell application "Safari" to quit'
                sleep 2
                rm -rf ~/Library/Safari/LastSession.plist
                osascript -e 'tell application "Safari" to activate'
                """;

        return executeCommand(script);
    }
    public static void deleteSafariHistory() {
        String script = "osascript -e 'tell application \"Safari\" to delete history items'";
        ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", script);
        try {
            Process process = processBuilder.start();
            process.waitFor();
            System.out.println("Safari history deleted successfully.");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.err.println("Error deleting Safari history.");
        }
    }

    /**
     * Deletes Chrome's last session.
     *
     * @return A message indicating success or failure.
     */
    private String deleteChromeSession() {
        String script = """
                pkill "Google Chrome"
                sleep 2
                rm -rf ~/Library/Application\\ Support/Google/Chrome/Default/Current\\ Session
                rm -rf ~/Library/Application\\ Support/Google/Chrome/Default/Current\\ Tabs
                open -a "Google Chrome"
                """;

        return executeCommand(script);
    }

    /**
     * Executes a shell command.
     *
     * @param script The shell command to execute.
     * @return The result of the execution.
     */
    private String executeCommand(String script) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", script);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            return "Last session deleted successfully. Output:\n" + output;
        } catch (Exception e) {
            return "Error deleting last session: " + e.getMessage();
        }
    }

     @PostMapping("/close")
    public String close(@RequestParam String browserName) {
        log.info("Attempting to close browser: {}", browserName);

        if (!System.getProperty("os.name").toLowerCase().contains("mac") &&
                !System.getProperty("os.name").toLowerCase().contains("nix") &&
                !System.getProperty("os.name").toLowerCase().contains("nux")) {
            return "This feature is supported only on macOS and Linux.";
        }

        try {
            // Step 1: Use `pgrep` to get the process ID of the browser
            ProcessBuilder pgrepBuilder = new ProcessBuilder("pgrep", "-f", browserName);
            Process pgrepProcess = pgrepBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(pgrepProcess.getInputStream()))) {
                String pid = reader.readLine(); // Get the first matching PID
                if (pid == null || pid.isEmpty()) {
                    log.info("No running process found for browser: {}", browserName);
                    return "No running process found for browser: " + browserName;
                }
                log.info("Found PID for browser {}: {}", browserName, pid);

                // Step 2: Use `kill` to terminate the process
                ProcessBuilder killBuilder = new ProcessBuilder("kill", "-9", pid);
                Process killProcess = killBuilder.start();
                killProcess.waitFor();
                log.info("Browser {} with PID {} terminated successfully.", browserName, pid);
                return "Browser " + browserName + " closed successfully.";
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error closing browser: {}", e.getMessage());
            return "Failed to close browser: " + browserName + ". Error: " + e.getMessage();
        }
    }
}
