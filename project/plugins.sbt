credentials += Credentials(Path.userHome / ".sbt" / ".credentials")
resolvers += "GitHub Package Registry" at "https://maven.pkg.github.com/precog/_"

addSbtPlugin("com.eed3si9n"    % "sbt-buildinfo"     % "0.9.0")
addSbtPlugin("com.precog"      % "sbt-precog-plugin" % "2.5.8-05dc5f2")
