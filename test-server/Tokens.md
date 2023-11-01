
token=$(az account get-access-token --resource 2e5bf60a-d982-4c30-9b87-8a69ce721d77 |  jq -r '.accessToken')

localhost:8080/ds/query -H "Authorization: Bearer $token"

## Test queries
curl -d "query=SELECT * WHERE {?a ?b ?c} LIMIT 10"


curl -d "SELECT * WHERE {?a ?b ?c} LIMIT 10" localhost:8080/ds/query