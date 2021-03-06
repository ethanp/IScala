package org.refptr.iscala

import java.io.File

import sbt.{
    ShowLines,
    Logger, ConsoleLogger,
    ModuleID,ModuleInfo,CrossVersion,Resolver,
    IvyPaths,InlineIvyConfiguration,IvySbt,IvyScala,IvyActions,
    InlineConfiguration,UpdateConfiguration,
    UpdateOptions,UpdateLogging,UnresolvedWarningConfiguration}

import Util.scalaVersion

object Modules {
    val Compiler = ModuleID("org.scala-lang", "scala-compiler", scalaVersion)
    val IScala = ModuleID("org.refptr.iscala", "IScala", "0.3-SNAPSHOT", crossVersion=CrossVersion.binary)
}

case class ClassPath(jars: Seq[File]) {
    def classpath: String = ClassPath.joinFiles(jars: _*)
}

object ClassPath {
    def joinFiles(paths: File*): String = join(paths map (_.getAbsolutePath): _*)
    def join(paths: String*): String = paths filterNot (_ == "") mkString File.pathSeparator
}

object Sbt {
    def resolve(modules: Seq[ModuleID], resolvers: Seq[Resolver]): Option[ClassPath] =
        resolve(modules, resolvers, ConsoleLogger())

    def resolve(modules: Seq[ModuleID], resolvers: Seq[Resolver], logger: Logger): Option[ClassPath] = {
        val paths = new IvyPaths(new File("."), None)
        val allResolvers = Resolver.withDefaultResolvers(resolvers)
        val ivyConf = new InlineIvyConfiguration(paths, allResolvers, Nil, Nil, false, None, Seq("sha1", "md5"), None, UpdateOptions(), logger)
        val ivySbt = new IvySbt(ivyConf)
        val binaryScalaVersion = CrossVersion.binaryScalaVersion(scalaVersion)
        val ivyScala = new IvyScala(scalaVersion, binaryScalaVersion, Nil, checkExplicit=true, filterImplicit=false, overrideScalaVersion=false)
        val settings = new InlineConfiguration(Modules.IScala, ModuleInfo("IScala"), modules, ivyScala=Some(ivyScala))
        val module = new ivySbt.Module(settings)
        val updateConf = new UpdateConfiguration(None, false, UpdateLogging.DownloadOnly)
        val updateReport = IvyActions.updateEither(module, updateConf, UnresolvedWarningConfiguration(), logger)
        updateReport match {
            case Right(report) =>
                Some(ClassPath(report.toSeq.map { case (_, _, _, jar) => jar }.distinct))
            case Left(warning) =>
                import ShowLines._
                warning.lines.foreach(logger.error(_))
                None
        }
    }
}
