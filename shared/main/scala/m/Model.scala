package m

sealed trait Model
case class ApiMessage(id:String, data:Model) extends Model