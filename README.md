# GFX Debuggers

This version-agnostic Fabric mod allows you to inject NSight or Renderdoc into Minecraft, without having to fiddle around with launchers, args or environment variables. At launch, it will ask you which debugger you want to attach, and injection can be skipped by closing the dialog.
Supports both Linux and Windows, and should work with any version of Minecraft, as long as Fabric was ported to it.
Requires Fabric Loader 0.10.7+ and Java 17+.

### Usage

1. Install Fabric.
2. Drop the mod into your mods folder
3. Launch the game, pick the debugger you want to use
4. Attach to the game process via your debugger of choice
5. Profit?

### Advanced usage

You can skip the dialog (useful for dev environments) by setting the `debugging` flag to the debugger you want.
Allowed options are:
```shell
-Ddebugger=nsight-frame
-Ddebugger=nsight-gpu
-Ddebugger=renderdoc
```

### NSight Graphics configuration

If the mod can't find your NSight installation, you can tell it where `ngfx` is:

Use **either** a JVM system property or an environment variable (the system property takes priority):

| Method | Example |
|---|---|
| **System property** | `-Dngfx.path=C:\Program Files\NVIDIA Corporation\Nsight Graphics 2025.5\host\windows-desktop-nomad-x64\ngfx.exe` |
| **Environment variable** | `NGFX_PATH=C:\Program Files\NVIDIA Corporation\Nsight Graphics 2025.5` |

Both accept a direct path to the `ngfx` executable, an NSight Graphics installation root directory, or a directory containing the executable directly.

#### Search paths

**Windows:**
- `%ProgramFiles%\NVIDIA Corporation\Nsight Graphics *\host\windows-desktop-nomad-x64\ngfx.exe`
- `%ProgramFiles(x86)%\NVIDIA Corporation\Nsight Graphics *\host\windows-desktop-nomad-x64\ngfx.exe`

**Linux:**
- `~/nvidia/NVIDIA-Nsight-Graphics-*/host/linux-desktop-nomad-x64/ngfx`

> [!TIP]
> NSight is kind of a pain to find, as many people install it on different drives and on linux it doesn't really have a standard location outside `~/nvidia/`. The newest version is preferred when multiple installations are found. If you have issues, check the logs to see where it's looking and add the path manually if needed.

### RenderDoc configuration

If the mod can't find your RenderDoc installation, you can tell it where the library is:

Use **either** a JVM system property or an environment variable (the system property takes priority):

| Method | Example |
|---|---|
| **System property** | `-Drenderdoc.path=C:\Program Files\RenderDoc` |
| **Environment variable** | `RENDERDOC_PATH=C:\Program Files\RenderDoc` |

Both accept either a direct path to the library file (`renderdoc.dll` / `librenderdoc.so`) **or** the directory containing it.

On Linux, the legacy environment variable `RENDERDOC_LIB_PATH` (expects a direct path to `librenderdoc.so`) is still supported.

#### Search paths

**Windows**

1. `-Drenderdoc.path` system property
2. `RENDERDOC_PATH` environment variable
3. `%ProgramFiles%\RenderDoc\renderdoc.dll`
4. Versioned folders in `%ProgramFiles%` (e.g. `RenderDoc v1.32`)
5. Same searches under `%ProgramFiles(x86)%`
6. `%USERPROFILE%\RenderDoc\renderdoc.dll`


**Linux**

1. `-Drenderdoc.path` system property
2. `RENDERDOC_PATH` environment variable
3. `RENDERDOC_LIB_PATH` environment variable (legacy)
4. Common system library paths (`/usr/lib64/renderdoc/`, `/usr/lib/`, `/usr/lib/x86_64-linux-gnu/`, `/usr/local/lib/`, etc.)
5. `~/.local/lib/librenderdoc.so`

> [!TIP]
> Paths are searched in the order listed, and the first valid library found is used. If it can't find a valid .dll/.so, it will bail out and won't inject. Everything should be logged if anything goes wrong, so check the logs if you have issues.
