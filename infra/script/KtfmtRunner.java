import com.facebook.ktfmt.format.Formatter;
import com.facebook.ktfmt.format.FormattingOptions;
import com.facebook.ktfmt.format.TrailingCommaManagementStrategy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Formats the given .kt files in place.
 *
 * <p>Invoked by ktfmt.sh, which compiles this on first use; it is not part of the infra build.
 */
public class KtfmtRunner {
    public static void main(String[] args) {
        boolean dryRun = false;
        int firstFile = 0;
        if (args.length > 0 && args[0].equals("--dry-run")) {
            dryRun = true;
            firstFile = 1;
        }

        // Edit together with IDEA's ktfmt.xml if changing these.
        FormattingOptions options = new FormattingOptions(
                /* maxWidth= */ 120,
                /* blockIndent= */ 4,
                /* continuationIndent= */ 4, TrailingCommaManagementStrategy.COMPLETE,
                /* removeUnusedImports= */ true,
                /* preserveLambdaBreaks= */ false,
                /* debuggingPrintOpsAfterFormatting= */ false);

        int changed = 0;
        int failed = 0;
        for (int i = firstFile; i < args.length; i++) {
            Path path = Path.of(args[i]);
            try {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                String formatted = Formatter.format(options, source);
                if (!formatted.equals(source)) {
                    changed++;
                    System.out.println((dryRun ? "Would reformat " : "Reformatted ") + path);
                    if (!dryRun) {
                        Files.writeString(path, formatted, StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception e) {
                failed++;
                System.err.println("Error formatting " + path + ": " + e.getMessage());
            }
        }

        if (failed > 0) {
            System.exit(1);
        }
        // Mirror ktfmt's --set-exit-if-changed in dry-run mode, so this is usable as a check.
        if (dryRun && changed > 0) {
            System.exit(1);
        }
    }
}
