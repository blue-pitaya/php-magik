<?php

namespace App;

use App\Models\User;

class Service
{
    public function handle()
    {
        $user = User::getAuthOnThrow();
        Missing::run();

        return \App\Models\User::class;
    }
}
