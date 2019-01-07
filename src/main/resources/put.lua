local sep = ":"
local path = KEYS[1]..sep
local resourcesPrefix = ARGV[1]
local collectionsPrefix = ARGV[2]
local expirableSet = ARGV[3]
local merge = ARGV[4]
local expiration = tonumber(ARGV[5])
local maxexpiration = tonumber(ARGV[6])
local resourceValue = ARGV[7]
local resourceHash = ARGV[8]
local lockPrefix = ARGV[9]
local lockOwner = ARGV[10]
local lockMode = ARGV[11]
local lockExpire = ARGV[12]
local compress = tonumber(ARGV[13])

if redis.call('exists',collectionsPrefix..KEYS[1]) == 1 then
    return "existingCollection"
end

if redis.call('exists',lockPrefix..KEYS[1]) == 1 then
    local result = redis.call('hmget',lockPrefix..KEYS[1],'owner','mode')
    if result[1] ~= lockOwner then
        return result[2]
    end
end

local setLockIfClaimed = function()
    if lockOwner ~= nil and lockOwner ~= '' then
        redis.call('hmset', lockPrefix..KEYS[1], 'owner', lockOwner, 'mode', lockMode)
        redis.call('pexpireat',lockPrefix..KEYS[1], lockExpire)
    end
end

local compressionModeNotChanged = function(doCompress)
    local isCompressed = redis.call('hexists',resourcesPrefix..KEYS[1],'compressed')
    return doCompress == isCompressed
end

if redis.call('exists',resourcesPrefix..KEYS[1]) == 1 then
    local etag = redis.call('hget',resourcesPrefix..KEYS[1],'etag')
    if etag == resourceHash and expiration == maxexpiration and compressionModeNotChanged(compress) then
        setLockIfClaimed()
        return "notModified";
    end
end

local not_empty = function(x)
    return (type(x) == "table") and (not x.err) and (#x ~= 0)
end

local pathState
local collections = {}
local nodes = {path:match((path:gsub("[^"..sep.."]*"..sep, "([^"..sep.."]*)"..sep)))}

for key,value in pairs(nodes) do
    if pathState == nil then
        pathState = value
    else
        collections[pathState] = value
        pathState = pathState..sep..value
    end
    redis.log(redis.LOG_NOTICE, "pathState: "..resourcesPrefix..pathState)
    redis.log(redis.LOG_NOTICE, "path: "..resourcesPrefix..KEYS[1])
    if redis.call('exists',resourcesPrefix..pathState) == 1 and resourcesPrefix..pathState ~= resourcesPrefix..KEYS[1] then
        return "existingResource".." "..resourcesPrefix..pathState
    end
end
for key,value in pairs(collections) do
    local collectionKey = collectionsPrefix..key
    local actualExpiration = expiration
    local contentMax = redis.call('zrange',collectionKey..sep..value,-1,-1, "withscores")[2]
    if contentMax ~= nil and contentMax ~= '' then
        contentMax = tonumber(contentMax)
        if contentMax > actualExpiration then
            actualExpiration = contentMax
        end
    end
    redis.log(redis.LOG_NOTICE, "zadd: "..collectionKey.." "..actualExpiration.." "..value)
    redis.call('zadd',collectionKey,actualExpiration,value)
end
redis.log(redis.LOG_NOTICE, "merge: "..merge)

if merge == "true" then
    local s = redis.call('hget',resourcesPrefix..KEYS[1],'resource')
    redis.log(redis.LOG_NOTICE, "merge: "..tostring(s).." "..resourceValue)
    if s then
        s = cjson.decode(s)
        for k,v in pairs(cjson.decode(resourceValue)) do
            if v == cjson.null then s[k] = nil else s[k] = v end
        end
        resourceValue = cjson.encode(s)
    end
end

redis.log(redis.LOG_NOTICE, "update: "..resourcesPrefix..KEYS[1])
if compress == 1 then
    redis.call('hmset',resourcesPrefix..KEYS[1],'resource',resourceValue,'etag',resourceHash,'compressed',1)
else
    redis.call('hmset',resourcesPrefix..KEYS[1],'resource',resourceValue,'etag',resourceHash)
    redis.call('hdel',resourcesPrefix..KEYS[1],'compressed')
end

if expiration ~= maxexpiration then
    redis.log(redis.LOG_NOTICE, "zadd: "..expirableSet.." "..expiration.." "..resourcesPrefix..KEYS[1])
    redis.call('zadd',expirableSet,expiration,resourcesPrefix..KEYS[1])
elseif expiration == maxexpiration then
    redis.log(redis.LOG_NOTICE, "zrem: "..expirableSet.." "..resourcesPrefix..KEYS[1])
    redis.call('zrem', expirableSet, resourcesPrefix..KEYS[1])
end

setLockIfClaimed()

return "OK";