/// Deterministic LRU used from a single owning actor. Costs are decoded bytes, not compressed file sizes.
public struct CostLimitedCache<Key: Hashable, Value> {
    private struct Entry { let value: Value; let cost: Int; var access: UInt64 }
    private var entries: [Key: Entry] = [:]
    private var clock: UInt64 = 0
    public let countLimit: Int
    public let costLimit: Int
    public private(set) var totalCost = 0
    public var count: Int { entries.count }

    public init(countLimit: Int, costLimit: Int) {
        self.countLimit = max(0, countLimit)
        self.costLimit = max(0, costLimit)
    }

    public mutating func value(for key: Key) -> Value? {
        guard var entry = entries[key] else { return nil }
        clock &+= 1
        entry.access = clock
        entries[key] = entry
        return entry.value
    }

    public mutating func insert(_ value: Value, for key: Key, cost: Int) {
        remove(key)
        guard countLimit > 0, cost >= 0, cost <= costLimit else { return }
        // Evict before adding, avoiding overflow when a caller supplies a very large cost.
        while entries.count >= countLimit || totalCost > costLimit - cost {
            guard let oldest = entries.min(by: { $0.value.access < $1.value.access })?.key else { break }
            remove(oldest)
        }
        clock &+= 1
        entries[key] = Entry(value: value, cost: cost, access: clock)
        totalCost += cost
    }

    public mutating func remove(_ key: Key) {
        if let old = entries.removeValue(forKey: key) { totalCost -= old.cost }
    }
    public mutating func remove(where predicate: (Key) -> Bool) {
        for key in entries.keys.filter(predicate) { remove(key) }
    }
    public mutating func removeAll() {
        entries.removeAll(keepingCapacity: false)
        totalCost = 0
    }
}
