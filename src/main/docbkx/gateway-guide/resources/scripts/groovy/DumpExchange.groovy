import org.forgerock.openig.http.Response
import groovy.json.JsonOutput

map = new LinkedHashMap(exchange)
map.remove("exchange")
map.remove("javax.servlet.http.HttpServletRequest")
map.remove("javax.servlet.http.HttpServletResponse")

json = JsonOutput.prettyPrint(JsonOutput.toJson(map))

exchange.response = new Response()
exchange.response.status = 200
exchange.response.entity = "<html><pre>" + json + "</pre></html>"
