# Stage 1: Build Angular Application
FROM node:18 AS build

# Set the working directory
WORKDIR /app

# Copy package.json and package-lock.json to install dependencies
COPY package*.json ./

# Install dependencies
RUN npm install

# Copy the rest of the application source code
COPY . .

# Build the Angular application for production
RUN npm run build --prod

# Stage 2: Serve the Angular Application
FROM nginx:alpine

# Copy built application from Stage 1
COPY --from=build /app/dist/frontend /usr/share/nginx/html

# Expose the port Nginx will listen on
EXPOSE 80

# Command to run Nginx in the foreground
CMD ["nginx", "-g", "daemon off;"]