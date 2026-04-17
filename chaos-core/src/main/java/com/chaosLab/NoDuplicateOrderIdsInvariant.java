package com.chaosLab;

public final class NoDuplicateOrderIdsInvariant implements Invariant {

    @Override
    public InvariantResult evaluate(ExperimentMetrics metrics) {
        long duplicateOrderIds = metrics.getDuplicateOrderIds();
        boolean passed = duplicateOrderIds == 0L;
        return new InvariantResult(
                "no_duplicate_order_ids",
                passed,
                "duplicateOrderIds=" + duplicateOrderIds + ", uniqueOrderIds=" + metrics.getUniqueOrderIds()
        );
    }
}
