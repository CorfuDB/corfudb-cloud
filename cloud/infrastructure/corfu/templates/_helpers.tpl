{{- define "corfu.name" -}}
corfu
{{- end }}

{{- define "corfu.labels" -}}
{{- if .Values.branch }}
branch: {{ .Values.branch | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- if .Values.commitSha }}
commitSha: {{ .Values.commitSha | trunc 63 | trimSuffix "-" }}
{{- end }}
type: {{ .Values.type | default "config" | quote }}
{{ include "corfu.selectorLabels" . }}
{{- end }}

{{- define "corfu.selectors" -}}
app.kubernetes.io/name: {{ include "corfu.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
