/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */
package com.servoy.j2db.util.keyword;


/**
 * Reserved SQL keywords list
 * @author jblok
 */
@SuppressWarnings("nls")
public class SQLKeywords
{
	//SQL Related
	public final static String[] keywords = new String[] { //

	// SQL related
	"truncate", 
	"type", 
	"number", 
	"index", 
	"modify", 
	"cobol",  
	"fortran", 
	"pascal", 
	"pl1", 
	"system", 
	"password", 
	"uuid", 
	"version",//problem for sybase 
	"release",//problem for sybase 
	"analyse",

		//begin, hypersonic small list special words
	"cached", 
	"datetime", 
	"limit", 
	"longvarbinary", 
	"longvarchar", 
	"object", 
	"other", 
	"temp", 
	"text", 
	"varchar_ignorecase", 
	//end, hypersonic small list special words
	//official ansi SQL 92 reserved list
	"absolute", 
	"action", 
	"add", 
	"all", 
	"allocate", 
	"alter", 
	"and", 
	"any", 
	"are", 
	"as", 
	"asc", 
	"assertion", 
	"at", 
	"authorization", 
	"avg", 
	"begin", 
	"between", 
	"bit", 
	"bit_length", 
	"both", 
	"by", 
	"cascade", 
	"cascaded", 
	"case", 
	"cast", 
	"catalog", 
	"char", 
	"character", 
	"char_length", 
	"character_length", 
	"check", 
	"close", 
	"coalesce", 
	"collate", 
	"collation", 
	"column", 
	"commit", 
	"connect", 
	"connection", 
	"constraint", 
	"constraints", 
	"continue", 
	"convert", 
	"corresponding", 
	"count", 
	"create", 
	"cross", 
	"current", 
	"current_date", 
	"current_time", 
	"current_timestamp", 
	"current_user", 
	"cursor", 
	"date", 
	"day", 
	"deallocate", 
	"dec", 
	"decimal", 
	"declare", 
	"default", 
	"deferrable", 
	"deferred", 
	"delete", 
	"desc", 
	"describe", 
	"descriptor", 
	"diagnostics", 
	"disconnect", 
	"distinct", 
	"domain", 
	"double", 
	"drop", 
	"else", 
	"end", 
	"end-exec", 
	"escape", 
	"except", 
	"exception", 
	"exec", 
	"execute", 
	"exists", 
	"external", 
	"extract", 
	"false", 
	"fetch", 
	"first", 
	"float", 
	"for", 
	"foreign", 
	"found", 
	"from", 
	"full", 
	"get", 
	"global", 
	"go", 
	"goto", 
	"grant", 
	"group", 
	"having", 
	"hour", 
	"identity", 
	"immediate", 
	"in", 
	"indicator", 
	"initially", 
	"inner", 
	"input", 
	"insensitive", 
	"insert", 
	"int", 
	"integer", 
	"intersect", 
	"interval", 
	"into", 
	"is", 
	"isolation", 
	"join", 
	"key", 
	"language", 
	"last", 
	"leading", 
	"left", 
	"level", 
	"like", 
	"local", 
	"lower", 
	"match", 
	"max", 
	"min", 
	"minute", 
	"module", 
	"month", 
	"names", 
	"national", 
	"natural", 
	"nchar", 
	"next", 
	"no", 
	"not", 
	"null", 
	"nullif", 
	"numeric", 
	"octet_length", 
	"of", 
	"on", 
	"only", 
	"open", 
	"option", 
	"or", 
	"order", 
	"outer", 
	"output", 
	"overlaps", 
	"pad", 
	"partial", 
	"position", 
	"precision", 
	"prepare", 
	"preserve", 
	"primary", 
	"prior", 
	"privileges", 
	"procedure", 
	"public", 
	"read", 
	"real", 
	"references", 
	"relative", 
	"restrict", 
	"revoke", 
	"right", 
	"rollback", 
	"rows", 
	"schema", 
	"scroll", 
	"second", 
	"section", 
	"select", 
	"session", 
	"session_user", 
	"set", 
	"show",
	"size", 
	"smallint", 
	"some", 
	"space", 
	"sql", 
	"sqlcode", 
	"sqlerror", 
	"sqlstate", 
	"substring", 
	"sum", 
	"system_user", 
	"table", 
	"temporary", 
	"then", 
	"time", 
	"timestamp", 
	"timezone_hour", 
	"timezone_minute", 
	"to", 
	"trailing", 
	"transaction", 
	"translate", 
	"translation", 
	"trim", 
	"true", 
	"union", 
	"unique", 
	"unknown", 
	"update", 
	"upper", 
	"usage", 
	"user", 
	"using", 
	"value", 
	"values", 
	"varchar", 
	"varying", 
	"view", 
	"when", 
	"whenever", 
	"where", 
	"with", 
	"work", 
	"write", 
	"year", 
	"zone", 

		//	Firebird
	"active", 
	"admin", 
	"after", 
	"ascending", 
	"auto", 
	"base_name", 
	"before", 
	"bigint", 
	"blob", 
	"break", 
	"cache", 
	"check_point_length", 
	"computed", 
	"conditional", 
	"connection_id", 
	"containing", 
	"cstring", 
	"current_role", 
	"database", 
	"debug", 
	"descending", 
	"do", 
	"entry_point", 
	"exit", 
	"file", 
	"filter", 
	"free_it", 
	"function", 
	"gdscode", 
	"generator", 
	"gen_id", 
	"group_commit_wait_time", 
	"if", 
	"inactive", 
	"index", 
	"input_type", 
	"lock", 
	"logfile", 
	"log_buffer_size", 
	"long", 
	"manual", 
	"maximum_segment", 
	"merge", 
	"message", 
	"module_name", 
	"nulls", 
	"num_log_buffers", 
	"output_type", 
	"overflow", 
	"page", 
	"pages", 
	"page_size", 
	"parameter", 
	"password", 
	"plan", 
	"post_event", 
	"protected", 
	"raw_partitions", 
	"rdb$db_key", 
	"record_version", 
	"recreate", 
	"reserv", 
	"reserving", 
	"retain", 
	"returning_values", 
	"returns", 
	"role", 
	"rows_affected", 
	"savepoint", 
	"segment", 
	"shadow", 
	"shared", 
	"singular", 
	"skip", 
	"snapshot", 
	"sort", 
	"stability", 
	"starting", 
	"starts", 
	"statistics", 
	"sub_type", 
	"suspend", 
	"transaction_id", 
	"trigger", 
	"variable", 
	"wait", 
	"weekday", 
	"while", 
	"yearday", 
	//firebird 1.5
	"current_connection", 
	"current_transaction", 
	"row_count", 
	"abs", 
	"boolean", 
	"skip",  
	"structural",  
	"deleting", 
	"inserting", 
	"leave", 
	"statement", 
	"updating", 
	"percent", 
	"temporary", 
	"ties", 

		// Sybase ASA
	"backup", 
	"bigint", 
	"binary", 
	"bottom", 
	"break", 
	"call", 
	"capability", 
	"char_convert", 
	"checkpoint", 
	"comment", 
	"compressed", 
	"contains", 
	"cube", 
	"dbspace", 
	"deleting", 
	"do", 
	"dynamic", 
	"elseif", 
	"encrypted", 
	"endif", 
	"existing", 
	"externlogin", 
	"forward", 
	"holdlock", 
	"identified", 
	"if", 
	"index", 
	"inout", 
	"inserting", 
	"install", 
	"instead", 
	"integrated", 
	"iq", 
	"lock", 
	"login", 
	"long", 
	"membership", 
	"message", 
	"mode", 
	"modify", 
	"new", 
	"noholdlock", 
	"notify", 
	"off", 
	"options", 
	"others", 
	"out", 
	"over", 
	"passthrough", 
	"print", 
	"proc", 
	"publication", 
	"raiserror", 
	"readtext", 
	"reference", 
	"release", 
	"remote", 
	"remove", 
	"rename", 
	"reorganize", 
	"resource", 
	"restore", 
	"return", 
	"rollup", 
	"save", 
	"savepoint", 
	"schedule", 
	"sensitive", 
	"setuser", 
	"share", 
	"start", 
	"stop", 
	"subtrans", 
	"subtransaction", 
	"synchronize", 
	"syntax_error", 
	"tinyint", 
	"top", 
	"tran", 
	"trigger", 
	"truncate", 
	"tsequal", 
	"unsigned", 
	"updating", 
	"validate", 
	"varbinary", 
	"variable", 
	"wait", 
	"waitfor", 
	"while", 
	"with_lparen", 
	"writetext", 

		// MySql
	"ignore", 
	"load", 

		// PostgreSQL
	"domains", 
	"offset", 

		// Oracle
	"uid", 
	"length", 

		// some more found in Hibernate dialects
	"account", 
	"alias", 
	"arith_overflow", 
	"artition", 
	"browse", 
	"bulk", 
	"class", 
	"clustered", 
	"compute", 
	"confirm", 
	"controlrow", 
	"count_big", 
	"dbcc", 
	"decrypt", 
	"determnistic", 
	"disk", 
	"dummy", 
	"dump", 
	"encrypt", 
	"endtran", 
	"errlvl", 
	"errordata", 
	"errorexit", 
	"exclusive", 
	"exist", 
	"exp_row_size", 
	"fillfactor", 
	"identity_gap", 
	"identity_start", 
	"jar", 
	"kill", 
	"lineno", 
	"materialized", 
	"max_rows_per_page", 
	"mirror", 
	"mirrorexit", 
	"nonclustered", 
	"nonscrollable", 
	"non_sensitive", 
	"numeric_truncation", 
	"offsets", 
	"once", 
	"online", 
	"perm", 
	"permanent", 
	"processexit", 
	"proxy_table", 
	"quiesce", 
	"readpast", 
	"reconfigure", 
	"reorg", 
	"replace", 
	"replication", 
	"reservepagegap", 
	"rowcount", 
	"rule", 
	"scrollable", 
	"semi_sensitive", 
	"shutdown", 
	"stringsize", 
	"stripe", 
	"summary", 
	"syb_identity", 
	"syb_restree", 
	"syb_terminate", 
	"textsize", 
	"title", 
	"tracefile", 
	"unpartition", 
	"use", 
	"user_option", 
	"xmlextract", 
	"xmlparse", 
	"xmltest", 
	"xmlvalidate" 
	};

	public static boolean checkIfKeyword(String name)
	{
		if (name == null) return false;
		String lname = name.trim().toLowerCase();
		for (String kw : keywords)
		{
			if (kw.equals(lname))
			{
				return true;
			}
		}

		return false;
	}
}
