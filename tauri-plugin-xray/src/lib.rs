use tauri::{
  plugin::{Builder, TauriPlugin},
  Manager, Runtime,
};

pub use models::*;

#[cfg(desktop)]
mod desktop;
#[cfg(mobile)]
mod mobile;

mod commands;
mod error;
mod models;

pub use error::{Error, Result};

#[cfg(desktop)]
use desktop::Xray;
#[cfg(mobile)]
use mobile::Xray;

/// Extensions to [`tauri::App`], [`tauri::AppHandle`] and [`tauri::Window`] to access the xray APIs.
pub trait XrayExt<R: Runtime> {
  fn xray(&self) -> &Xray<R>;
}

impl<R: Runtime, T: Manager<R>> crate::XrayExt<R> for T {
  fn xray(&self) -> &Xray<R> {
    self.state::<Xray<R>>().inner()
  }
}

/// Initializes the plugin.
pub fn init<R: Runtime>() -> TauriPlugin<R> {
  Builder::new("xray")
    .invoke_handler(tauri::generate_handler![commands::ping])
    .setup(|app, api| {
      #[cfg(mobile)]
      let xray = mobile::init(app, api)?;
      #[cfg(desktop)]
      let xray = desktop::init(app, api)?;
      app.manage(xray);
      Ok(())
    })
    .build()
}
