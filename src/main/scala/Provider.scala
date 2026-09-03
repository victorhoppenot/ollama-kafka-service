import java.net.http.HttpClient
import io.circe.Json
trait Provider {
    def query(q: Query)(implicit client: HttpClient): Response

    def insertModelIntoPayload(model: String, payload: Json): Json    
  
    def activeModels(implicit client: HttpClient): List[String]
}
