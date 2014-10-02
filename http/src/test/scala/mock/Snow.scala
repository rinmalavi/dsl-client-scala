package mock

case class Snow(val URI: String = "uri_value") extends com.dslplatform.api.patterns.Snowflake[Agg] {
}

object UserSnow extends com.dslplatform.api.client.SnowflakeCompanion[Snow, Agg]{
}
