# NB! This project designed to build with LuaLaTeX
.PHONY: build
build: clean
		latexmk -synctex=1 \
						-interaction=nonstopmode \
						-file-line-error \
						-pdf \
						-outdir=build \
						-lualatex \
						-shell-escape main.tex

.PHONY: clean
clean:
		rm build/main.pdf || true
