#!/usr/bin/env bash
set -euo pipefail

read -rsp "Nova password: " password
printf '\n'
read -rsp "Confirmar password: " confirm
printf '\n'

if [[ -z "${password}" ]]; then
  echo "Password vazia nao e permitida." >&2
  exit 1
fi

if [[ "${password}" != "${confirm}" ]]; then
  echo "As passwords nao coincidem." >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"
pom_file="${project_dir}/pom.xml"

if [[ ! -f "${pom_file}" ]]; then
  echo "Nao encontrei pom.xml em ${project_dir}." >&2
  echo "Move este script para a pasta scripts/ do RucoPlan ou corre-o a partir do repo." >&2
  exit 1
fi

echo "A preparar classpath Maven..." >&2
mvn -q -f "${pom_file}" dependency:build-classpath \
  -DincludeScope=runtime \
  -Dmdep.outputFile="${tmp_dir}/classpath.txt"

classpath="$(cat "${tmp_dir}/classpath.txt")"
if [[ -z "${classpath}" ]]; then
  echo "Nao foi possivel obter o classpath Maven." >&2
  exit 1
fi

cat > "${tmp_dir}/GenerateBcrypt.java" <<'JAVA'
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateBcrypt {
    public static void main(String[] args) {
        System.out.println(new BCryptPasswordEncoder().encode(args[0]));
    }
}
JAVA

echo "A compilar gerador temporario..." >&2
javac -cp "${classpath}" "${tmp_dir}/GenerateBcrypt.java"

echo "Hash BCrypt gerado:" >&2
java -cp "${tmp_dir}:${classpath}" GenerateBcrypt "${password}"
