/**
 * Non-recurring background jobs to be executed asynchronously, outside the context of an HTTP request.
 */
@Allow
@Require({SQLDialect.H2, SQLDialect.POSTGRES})
@NonNullApi
@NonNullFields
package de.rptu.cs.exclaim.jobs;

import org.jooq.Allow;
import org.jooq.Require;
import org.jooq.SQLDialect;
import org.springframework.lang.NonNullApi;
import org.springframework.lang.NonNullFields;
