local function split(str,sep)
    local array = {}
    local reg = string.format("([^%s]+)",sep)
    for mem in string.gmatch(str,reg) do
        table.insert(array, mem)
    end
    return array
end

function count(tuple, action, campaign, period)
  local count = 0
  if aerospike:exists(tuple) then
    local events = tuple["events"] 
    for i, v in map.pairs(events) do
      if i > period then
        local parts = split(v, ":")
        local action_part = parts[2]
        local campaign_part = parts[1]
        if action_part == action and campaign_part == campaign then
          count = count + 1 
        end
      end
    end
  end 
  return count
end

