web: link_web run_vite

run_api:
	sbt "~api/run"

link_web:
	sbt "~web/fastLinkJS"

run_vite:
	npm run dev

