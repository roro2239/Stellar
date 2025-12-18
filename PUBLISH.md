# 发布到 GitHub Packages

本文档说明如何将 Stellar API 发布到 GitHub Packages。

## 发布步骤

### 使用 GitHub Actions 自动发布

创建 `.github/workflows/publish.yml`：

```yaml
name: Publish to GitHub Packages

on:
  release:
    types: [created]

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Publish to GitHub Packages
        run: ./gradlew publish
        env:
          GITHUB_ACTOR: ${{ github.actor }}
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

配置完成后，每次在 GitHub 上创建 Release 时会自动发布。

### 发布流程

1. 确保代码已推送到 GitHub
2. 在 GitHub 仓库页面点击 "Releases" → "Create a new release"
3. 创建新的 tag（如 `v1.0`）
4. 填写 Release 标题和说明
5. 点击 "Publish release"
6. GitHub Actions 会自动构建并发布到 GitHub Packages

## 使用已发布的包

### 前置准备

使用 GitHub Packages 需要 GitHub Personal Access Token：

1. 访问 GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
2. 点击 "Generate new token (classic)"
3. 勾选 `read:packages` 权限
4. 生成并保存 token

### 配置仓库

其他项目要使用 Stellar API，需要在 `settings.gradle` 中添加：

```gradle
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/RoRoStudio/Stellar")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

在本地开发时，设置环境变量：

```bash
export GITHUB_ACTOR=你的GitHub用户名
export GITHUB_TOKEN=你的Personal_Access_Token
```

然后在 `build.gradle` 中添加依赖：

```gradle
dependencies {
    implementation 'com.github.rorostudio:api:1.0'
    implementation 'com.github.rorostudio:provider:1.0'
    implementation 'com.github.rorostudio:aidl:1.0'
    implementation 'com.github.rorostudio:shared:1.0'
}
```

## 版本管理

API 版本号在 `publish.gradle` 中定义，独立于应用版本：

```gradle
version = '1.0'
```

**注意：** API 版本号与 Stellar 管理器应用版本号独立管理。应用可能是 1.0.4，但 API 保持 1.0 版本。

发布新版本时，更新 `publish.gradle` 中的 `version` 并重新发布即可。

## 验证发布

发布成功后，可以在以下位置查看：

- GitHub 仓库页面 → Packages
- 或访问：https://github.com/RoRoStudio/Stellar/packages

## 常见问题

### Q: GitHub Actions 发布失败

**A:** 检查 workflow 文件配置是否正确，确保 `permissions` 包含 `packages: write`。

### Q: 其他用户无法下载包

**A:** GitHub Packages 需要认证才能下载。用户需要：
1. 创建 GitHub Token（至少需要 `read:packages` 权限）
2. 配置环境变量 `GITHUB_ACTOR` 和 `GITHUB_TOKEN`

### Q: 如何删除已发布的版本

**A:** 在 GitHub 仓库的 Packages 页面，选择对应的包版本，点击 "Delete" 即可。

### Q: 在 CI/CD 中如何使用

**A:** 在 GitHub Actions 中可以直接使用 `${{ secrets.GITHUB_TOKEN }}`，无需额外配置。

## 注意事项

1. **私有仓库：** 如果仓库是私有的，其他用户需要有仓库访问权限才能下载包
2. **公开仓库：** 即使仓库是公开的，下载包仍需要 GitHub Token 认证
3. **版本不可变：** 一旦发布某个版本，不能修改该版本的内容，只能删除后重新发布
4. **Token 安全：** 使用环境变量存储 Token，不要硬编码在代码中
