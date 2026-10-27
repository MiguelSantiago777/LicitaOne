# Atalhos de desenvolvimento. No Windows, use o Git Bash (ou rode os comandos do README).
.PHONY: up down infra backend frontend test e2e api

up:
	docker compose up -d --build

down:
	docker compose down

infra:
	docker compose up -d postgres rabbitmq s3 mailpit

backend:
	cd backend && ./mvnw spring-boot:run

frontend:
	cd frontend && npm start

test:
	cd backend && ./mvnw -B verify
	cd frontend && npm test && npm run lint

e2e:
	cd e2e && npx playwright test

api:
	cd frontend && npm run api:fetch && npm run api:gen
