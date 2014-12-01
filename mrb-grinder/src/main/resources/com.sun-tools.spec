
Name:           mrb-@NAME@
Version:        @VERSION@
Release:        @RELEASE@
Summary:        @NAME@, from the local file system

Group:          Java/MrB
Vendor:         Maven RPM Bridge
License:        @LICENSE@

BuildArch:      noarch
BuildRoot:      %{_tmppath}/%{name}-%{version}-%{release}-root-%(%{__id_u} -n)

Requires: mrb-javax-module-v1
Requires: java-sdk

%description

This RPM adapts the local (RPM-packaged) "tools.jar" from the JDK to be addressed as a
modular dependency, without actually including the jar (as it varies from one version
to the next).

TODO: how can we make sure we are getting the 'correct' version; if (for example) the user has many installed and launches with a different java version?

%install
rm -rf $RPM_BUILD_ROOT
mkdir  $RPM_BUILD_ROOT
cd     $RPM_BUILD_ROOT

mkdir -p  ./usr/share/java/@NAME@
chmod 700 ./usr/share/java/@NAME@

ln -s /usr/lib/jvm/java/lib/tools.jar ./usr/share/java/@NAME@/@MODULE_NAME@.jar

cat -> ./usr/share/java/@NAME@/@MODULE_NAME@.deps <<EOF
@DEPS_FILE_CONTENTS@
EOF

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(644,root,root,755)
/usr/share/java/@NAME@
%config /usr/share/java/@NAME@/@MODULE_NAME@.deps

