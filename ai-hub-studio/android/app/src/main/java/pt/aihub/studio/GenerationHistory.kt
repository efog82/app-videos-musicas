package pt.aihub.studio

/** Removing a report is independent of deleting any media, project or conversation. */
class GenerationHistory(private val db:LocalData) {
    fun remove(id:String) {
        val sql=db.writableDatabase
        sql.beginTransaction()
        try {
            val task=sql.query("records",arrayOf("body"),"id=? AND kind=?",arrayOf(id,"generation"),null,null,null).use{cursor->if(cursor.moveToFirst())org.json.JSONObject(cursor.getString(0)) else null}
            if(task!=null){
                require(task.optString("state") in setOf("failed","cancelled","interrupted","done")){"O pedido ainda está em curso. Cancele-o e aguarde antes de eliminar o relatório."}
                sql.delete("records","id=? AND kind=?",arrayOf(id,"generation"))
            }
            sql.setTransactionSuccessful()
        }finally{sql.endTransaction()}
    }
}
