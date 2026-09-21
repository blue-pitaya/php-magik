local HOME = vim.env.PHP_MAGIK_HOME or "/var/www"

local function server_cmd(root)
  return {
    "java",
    "-Djava.library.path=" .. HOME .. "/target/native",
    -- one argument: the /* is expanded by the JVM, not by a shell
    "-cp",
    HOME .. "/target/classes:" .. HOME .. "/target/dependency/*",
    "dev.bluepitaya.phpmagik.Main",
    -- the tree the server indexes, scanned once at startup
    "--path",
    root,
  }
end

local function on_attach(client, bufnr)
  local function map(lhs, rhs, desc)
    vim.keymap.set("n", lhs, rhs, { buffer = bufnr, desc = "php-magik: " .. desc })
  end

  -- 0.11 already binds most of these; set them so 0.10 works too
  map("K", vim.lsp.buf.hover, "hover")
  map("gd", vim.lsp.buf.definition, "definition")
  map("grr", vim.lsp.buf.references, "references")
  map("gO", vim.lsp.buf.document_symbol, "document symbols")
  map("<leader>ws", function()
    vim.lsp.buf.workspace_symbol("")
  end, "workspace symbols")

  vim.bo[bufnr].omnifunc = "v:lua.vim.lsp.omnifunc"
  if vim.lsp.completion then
    -- the server's trigger characters are > $ :
    vim.lsp.completion.enable(true, client.id, bufnr, { autotrigger = true })
  end
end

vim.api.nvim_create_autocmd("FileType", {
  pattern = "php",
  group = vim.api.nvim_create_augroup("PhpMagik", { clear = true }),
  callback = function(args)
    local root = vim.fs.root(args.buf, { "composer.json", ".git" }) or vim.fn.getcwd()

    vim.lsp.start({
      name = "php-magik",
      root_dir = root,
      cmd = server_cmd(root),
      on_attach = on_attach,
    }, { bufnr = args.buf })
  end,
})

-- :PhpMagikLog  - the server's own request log
vim.api.nvim_create_user_command("PhpMagikLog", function()
  vim.cmd.tabnew("/tmp/php-magik.log")
end, { desc = "open the php-magik server log" })

-- :PhpMagikRestart  - the index is built at startup, so anything created
-- after the server launched needs a fresh one
vim.api.nvim_create_user_command("PhpMagikRestart", function()
  for _, client in ipairs(vim.lsp.get_clients({ name = "php-magik" })) do
    client:stop()
  end
  vim.defer_fn(function()
    vim.cmd.edit()
  end, 500)
end, { desc = "restart php-magik and reindex" })
