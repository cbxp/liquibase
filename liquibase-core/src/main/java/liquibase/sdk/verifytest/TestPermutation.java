package liquibase.sdk.verifytest;

import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.sdk.exception.UnexpectedLiquibaseSdkException;
import liquibase.util.MD5Util;
import liquibase.util.StringUtils;

import java.util.*;

public class TestPermutation {

    private String notRanMessage;
    private SortedMap<String, Value> data = new TreeMap<String, Value>();
    private SortedMap<String,Value> description = new TreeMap<String, Value>();
    private String key = "";
    private String longKey = "";
    private SortedMap<String,Value> notes = new TreeMap<String, Value>();

    private List<Setup> setupCommands = new ArrayList<Setup>();
    private List<Verification> verifications = new ArrayList<Verification>();
    private List<Cleanup> cleanupCommands = new ArrayList<Cleanup>();

    private boolean valid = true;
    private boolean verified = false;
    private boolean canVerify;

    public static OkResult OK = new OkResult();

    public TestPermutation(VerifiedTest test) {
        test.addPermutation(this);
    }

    public String getKey() {
        return key;
    }

    public boolean getCanVerify() {
        return canVerify;
    }

    public void setCanVerify(boolean canVerify) {
        this.canVerify = canVerify;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getNotRanMessage() {
        return notRanMessage;
    }

    public void setNotRanMessage(String notRanMessage) {
        this.notRanMessage = notRanMessage;
    }

    public List<Setup> getSetup() {
        return setupCommands;
    }

    public void addAssertion(Setup setup) {
        this.setupCommands.add(setup);
    }

    public void addSetup(Setup setup) {
        this.setupCommands.add(setup);
    }

    public SortedMap<String, Value> getDescription() {
        return description;
    }

    public String getLongKey() {
        return longKey;
    }

    public SortedMap<String, Value> getNotes() {
        return notes;
    }

    public SortedMap<String, Value> getData() {
        return data;
    }

    public void describe(String key, Object value) {
        describe(key, value, OutputFormat.DefaultFormat);
    }

    public void describe(String key, Object value, OutputFormat outputFormat) {
        description.put(key, new Value(value, outputFormat));
        recomputeKey();
    }

    protected void recomputeKey() {
        longKey = StringUtils.join(description, ",", new StringUtils.StringUtilsFormatter() {
            @Override
            public String toString(Object obj) {
                return ((Value) obj).serialize();
            }
        });
        key = MD5Util.computeMD5(longKey);
    }

    public void note(String key, Object value) {
        note(key, value, OutputFormat.DefaultFormat);
    }

    public void note(String key, Object value, OutputFormat outputFormat) {
        notes.put(key, new Value(value, outputFormat));
    }

    public void data(String key, Object value) {
        data(key, value, OutputFormat.DefaultFormat);
    }

    public void data(String key, Object value, OutputFormat outputFormat) {
        data.put(key, new Value(value, outputFormat));
    }

    public List<Verification> getVerifications() {
        return verifications;
    }

    public void addVerification(Verification verification) {
        verifications.add(verification);
    }

    public List<Cleanup> getCleanup() {
        return cleanupCommands;
    }

    public void addCleanup(Cleanup cleanup) {
        cleanupCommands.add(cleanup);
    }

    public void test(VerifiedTest test) throws Exception {
        TestPermutation previousRun = VerifiedTestFactory.getInstance().getSavedRun(test, this);

        if (notRanMessage != null) {
            save(test);
            return;
        }

        try {
            for (Setup setup : this.setupCommands) {
                SetupResult result = setup.run();

                if (result == null) {
                    throw new UnexpectedLiquibaseException("No result returned by setup");
                } else {
                    if (!result.isValid()) {
                        valid = false;
                        canVerify = false;
                        notRanMessage = result.getMessage();
                        break;
                    } else if (!result.canVerify()) {
                        canVerify = false;
                        notRanMessage = result.getMessage();
                    }
                }
            }
        } catch (Throwable e) {
            String message = "Error executing setup\n"+
                    "Description: "+ output(description)+"\n"+
                    "Notes: "+output(notes)+"\n"+
                    "Data: "+output(data);
            throw new RuntimeException(message, e);
        }

        if (!valid || !canVerify) {
            save(test);
            return;
        }

        Exception cleanupError = null;
        try {
            try {
                for (Verification verification : this.verifications) {
                    verification.run();
                }
            } catch (CannotVerifyException e) {
                this.verified = false;
            } catch (Throwable e) {
                String message = "Error executing verification\n"+
                        "Description: "+ output(description)+"\n"+
                        "Notes: "+output(notes)+"\n"+
                        "Data: "+output(data);
                throw new RuntimeException(message, e);
            }
            this.verified = true;
        } finally {
            for (Cleanup cleanup : cleanupCommands) {
                try {
                    cleanup.run();
                } catch (Exception e) {
                    cleanupError = e;
                }
            }
        }

        if (cleanupError != null) {
            throw new UnexpectedLiquibaseSdkException("Cleanup error", cleanupError);
        }

        save(test);
    }

    protected void save(VerifiedTest test) throws Exception {
        VerifiedTestFactory.getInstance().saveRun(test, this);
    }

    private String output(SortedMap<String, Value> map) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Value> entry : map.entrySet()) {
            out.add(entry.getKey()+"="+entry.getValue().serialize());
        }

        return StringUtils.join(out, ", ");
    }

    public boolean getVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public static interface SetupResult {
        boolean isValid();
        boolean canVerify();
        String getMessage();
    }

    public static class Invalid implements SetupResult {

        private String message;

        public Invalid(String message) {
            this.message = message;
        }

        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public boolean canVerify() {
            return false;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    public static class CannotVerify implements SetupResult {

        private String message;

        public CannotVerify(String message) {
            this.message = message;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean canVerify() {
            return false;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }


    public static class OkResult implements SetupResult {

        public OkResult() {
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public boolean canVerify() {
            return true;
        }

        @Override
        public String getMessage() {
            return null;
        }
    }



    public static interface Setup {
        public SetupResult run();
    }

    public static interface Verification {
        public void run();
    }

    public static interface Cleanup {
        public void run();
    }

    public static class CannotVerifyException extends RuntimeException {
        public CannotVerifyException(String message) {
            super(message);
        }

        public CannotVerifyException(String message, Throwable cause) {
            super(message, cause);
        }
    }


    public static class Value {
        private Object value;
        private OutputFormat format;

        public Value(Object value, OutputFormat format) {
            this.value = value;
            this.format = format;
        }

        public String serialize() {
            return format.format(value);
        }
    }

}
