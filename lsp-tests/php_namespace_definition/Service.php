<?php

namespace App;

use App\Models\User as UserModel;
use App\Support\View;

class Service
{
    public function render(View $view, UserModel $user)
    {
        $userId = $user->id;

        return $view->name.' '.$userId;
    }
}
