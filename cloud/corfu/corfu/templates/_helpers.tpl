{{- define "corfu.name" -}}
corfu
{{- end }}

{{- define "corfu.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "corfu.labels" -}}
{{- if .Values.branch }}
branch: {{ .Values.branch | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- if .Values.commitSha }}
commitSha: {{ .Values.commitSha | trunc 63 | trimSuffix "-" }}
{{- end }}
type: {{ .Values.type | default "config" | quote }}
{{ include "corfu.selectors" . }}
{{- end }}

{{- define "corfu.selectors" -}}
app.kubernetes.io/name: {{ include "corfu.name" . }}
{{- end }}


{{- define "corfu.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "corfu.name" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}