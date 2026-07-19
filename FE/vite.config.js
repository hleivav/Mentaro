import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      // ilustracion-marca.png (6MB) NO va aca a proposito: no se usa en
      // ningun lugar de la app todavia (queda disponible para splash o
      // marketing), precachearla infla el service worker sin necesidad y
      // supera el limite de 2MB de workbox.
      includeAssets: ['icons/favicon-32x32.png'],
      manifest: {
        name: 'Mentaro',
        short_name: 'Mentaro',
        description: 'Convierte cualquier documento en una experiencia de aprendizaje jugable',
        theme_color: '#faf6ee',
        background_color: '#faf6ee',
        display: 'standalone',
        start_url: '/',
        icons: [
          {
            src: 'icons/icono-192x192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'icons/icono-512x512.png',
            sizes: '512x512',
            type: 'image/png'
          },
          {
            src: 'icons/icono-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable'
          }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,webmanifest}'],
        // El glob de arriba agarraria tambien ilustracion-marca.png (6MB,
        // sin uso todavia en la app) y supera el limite de precacheo de
        // workbox (2MB) - se excluye explicitamente en vez de subir el
        // limite, que seria precachear un archivo que nadie pide todavia.
        globIgnores: ['icons/ilustracion-marca.png']
      },
      devOptions: {
        enabled: false
      }
    })
  ],
  server: {
    port: 5173
  }
})
