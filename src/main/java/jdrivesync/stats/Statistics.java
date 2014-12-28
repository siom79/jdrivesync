package jdrivesync.stats;

public class Statistics {
    private static final Statistics statistics = new Statistics();
    private long created;
    private long deleted;
    private long updated;
    private long unchanged;

    private Statistics() {
        //intentionally left empty
    }

    public static Statistics getInstance() {
        return statistics;
    }

    void created() {
        created++;
    }

    void deleted() {
        deleted++;
    }

    void updated() {
        updated++;
    }

    void unchanged() {
        unchanged++;
    }

    public long getCreated() {
        return created;
    }

    public long getDeleted() {
        return deleted;
    }

    public long getUpdated() {
        return updated;
    }

    public long getUnchanged() {
        return unchanged;
    }
}
