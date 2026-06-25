redis.replicate_commands()

local seqKey     = KEYS[1]
local epoch      = tonumber(ARGV[1])
local batchSize  = tonumber(ARGV[2])

local t = redis.call('TIME')
local nowMs = t[1] * 1000 + math.floor(t[2] / 1000)
local tsPart = bit.band(nowMs - epoch, 0x1FFFFFFFFFF)

local seqKeyWithTs = seqKey .. ":" .. nowMs
local seqEnd = redis.call('INCRBY', seqKeyWithTs, batchSize)
redis.call('PEXPIRE', seqKeyWithTs, 3000)
local seqStart = seqEnd - batchSize + 1

return {nowMs, seqStart, seqEnd}
